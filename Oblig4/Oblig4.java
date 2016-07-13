import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.Arrays;

// Assignment: http://www.uio.no/studier/emner/matnat/ifi/INF2440/v16/obliger/oblig4/oblig4c.pdf

class Oblig4{
	NPunkter np;
	// Lagrer koordinatene til punktene på innhyllinga
	IntList coHull, paraHull;
	// Koordinatsystem
	int[] x, y;
	double[] sekvTime, paraTime;
	int len, n, minx, maxx, maxy, numberOfCores;
	// Lagrer største verdiene av x og y, og minste verdien av x.
	int MAX_X, MAX_Y, MIN_X;
	// Parallell data for å finne div x og y
	int[] localStaticX, localStaticY, threadMaxx, threadMinx;
	CyclicBarrier allThreads, para;

	public static void main(String[] args) {
		new Oblig4().init();		
	}

	private void init() {
		sekvTime = new double[5];
		paraTime = new double[5];
		int utfall = 100;
		for(int j = 100; j < 100000000; j = j *10){ 
			for(int i = 0; i < 5; i++) {
				// Sekvensiell
				long time = System.nanoTime();
				sekvMetode(j);
				sekvTime[i] = (System.nanoTime()-time)/1000000.0;

				// Nullstille verdier
				minx = 0; maxx = 0; MAX_X = 0; MAX_Y = 0;
				// Parallelt
				time = System.nanoTime();
				paraMetode(j);
				paraTime[i] = (System.nanoTime()-time)/1000000.0;
			}
			Arrays.sort(sekvTime);
			Arrays.sort(paraTime);
			System.out.println("\nn = " + j);
			System.out.println("Sekvensiell: " + sekvTime[2] + "ms");
			System.out.println("Parallell: " + paraTime[2] + "ms");
			System.out.println("Speedup: " + sekvTime[2]/paraTime[2]);
			if(j == 1000) {
				new TegnUt(this, coHull, "Sekvensiell");
				new TegnUt(this, paraHull, "Parallell");
			}
			System.out.println("Size: "+ coHull.size() + " " + paraHull.size());
		}
	}

	private void paraMetode(int size) {
		len = size;
		n = len;
		// Parameter = Størrelse på problemet
		np = new NPunkter(len);
		x = new int[len]; y = new int[len];
		np.fyllArrayer(x, y);
		paraHull = new IntList();
		numberOfCores = Runtime.getRuntime().availableProcessors();
		allThreads = new CyclicBarrier(numberOfCores+1);
		para = new CyclicBarrier(2);

		// Finne størst x og y;
		localStaticX = new int[numberOfCores];
		localStaticY = new int[numberOfCores];
		threadMaxx = new int[numberOfCores];
		threadMinx = new int[numberOfCores];	
		for(int i = 0; i < numberOfCores; i++){
			new Thread(new Worker(i)).start();
		}
		// Vente på trådene
		try{
			allThreads.await();
		} catch (Exception e) {}
		// Finne største verdien i trådene
		findValues();

		// Starten på løsningen på parallell innhylling 
		new Thread(new ParaSolution(0, numberOfCores/2, null, null, 0, 0, 0)).start();
		try{
			para.await();
		} catch (Exception e){}
	}

	// Finner riktig verdier for MAX_X, MAX_Y, minx og maxx etter kjøringen av Worker
	private void findValues() {
		Arrays.sort(localStaticX);
		Arrays.sort(localStaticY);
		MAX_X = localStaticX[numberOfCores-1];
		MAX_Y = localStaticY[numberOfCores-1];
		minx = threadMinx[0];
		maxx = threadMaxx[0];

		int minTmp = 0, maxTemp = 0;
		for(int i = 1; i < numberOfCores; i++) {
			if(x[threadMinx[i]] < x[minx]) minx = threadMinx[i]; 
			if(x[threadMaxx[i]] > x[maxx]) maxx = threadMaxx[i];

		}
	}

	private void sekvMetode(int size) {
		// initialisering
		len = size;
		n = len;
		// Parameter = Størrelse på problemet
		np = new NPunkter(len);
		x = new int[len]; y = new int[len];
		np.fyllArrayer(x, y);
		// Parameter = størrelse. Default er 16.
		coHull = new IntList();
        findXY();
       	findStaticXY();
		IntList coordniates = new IntList();
        for(int i = 0; i < x.length; i++) {
            coordniates.add(i);
        }
        // Finne punktene over
        // Finne alle punktene over linjen
        IntList list = getCorrespondingCoordinates(maxx, minx, coordniates);
        // Legge til første punktet i coHull
        coHull.add(maxx);
        int p3 = getOuterPoint(maxx, minx, list);
        sekvRek(maxx, minx, p3, list, coHull);

        // Finne punktene under
        list = getCorrespondingCoordinates(minx, maxx, coordniates);
        // Legge til det "midterste punktet"
        coHull.add(minx);

        int lowY = getOuterPoint(minx, maxx, list);
        sekvRek(minx, maxx, lowY, list, coHull);
	}

	private void sekvRek(int p1, int p2, int p3, IntList p, IntList m) {
       	int right = getOuterPoint(p1, p3, p);
        if(right >= 0) {
            sekvRek(p1, p3, right, getCorrespondingCoordinates(p1, p3, p), m);
        }

        m.add(p3);

        int left = getOuterPoint(p3, p2, p);
        if(left >= 0) {
            sekvRek(p3, p2, left, getCorrespondingCoordinates(p3, p2, p), m);
        }
	}
	// Finne noder som ligger utenfor linja
	private IntList getCorrespondingCoordinates(int p1, int p2, IntList p) {
		IntList res = new IntList();
        for(int i = 0; i < p.size(); i++) {
            if(getSpan(p1, p2, p.get(i)) <= 0.0) { // sjekke om punktet skal være med, punkter å linja blir tatt med.
                res.add(p.get(i));
            }
        }
        return res;
	}

	// Returnerer distansen fra et punkt til linja ved å bruke formelen ax + by + c = 0.
    private double getSpan(int p1, int p2, int p3) {
    	int a = y[p1] - y[p2];
    	int b = x[p2] - x[p1];
    	int c = (y[p2] * x[p1]) - (y[p1] * x[p2]);

        return ((a * x[p3]) + (b * y[p3]) + c);
    }

    // Finne det ytterste punktet i p fra linja p1-p2
    private int getOuterPoint(int p1, int p2, IntList p) {
        double test = 0.0;
        int tmp = Integer.MIN_VALUE;

        for(int i = 0; i < p.size(); i++) {
            double span = getSpan(p1, p2, p.get(i));
            if(span == 0.0) {
                if(x[p.get(i)] < x[p2] && x[p.get(i)] > x[p1] && (y[p.get(i)] == y[p1] && y[p.get(i)] == y[p2])) {
                    return p.get(i);
                } else if (y[p.get(i)] < y[p1] && y[p.get(i)] > y[p2] && (x[p.get(i)] == x[p1] && x[p.get(i)] == x[p2])) {
                    return p.get(i);
                } else if (y[p.get(i)] < y[p2] && y[p.get(i)] > y[p1] && (x[p.get(i)] == x[p1] && x[p.get(i)] == x[p2])) {
                    return p.get(i);
                } else if (x[p.get(i)] < x[p1] && x[p.get(i)] > x[p2] && (y[p.get(i)] == y[p1] && y[p.get(i)] == y[p2])) {
                    return p.get(i);
                } 
            } else if (test >= span) {
                test = span;
                tmp = p.get(i);
            }
        }
        return tmp;
    }

	// Finne største og minste x verdi, og største y. Setter 'minx', 'maxx' og 'maxy'. Testet at den funker. 
	private void findXY() {
		// indexes
		int lSmall = -1, lBig = -1, yLarge = -1;
		// Values
		int smallValue = Integer.MAX_VALUE, largeValue = Integer.MIN_VALUE, largeYValue = Integer.MIN_VALUE;
		for(int i = 0; i < x.length; i++) {
			if(x[i] < smallValue) {smallValue = x[i]; lSmall = i;}	
			if(x[i] > largeValue) {lBig = i; largeValue = x[i];}
			if(y[i] > largeYValue) {yLarge = i; largeYValue = y[i];}
		}
		maxx = lBig; minx = lSmall; maxy = yLarge;
	}

	// Prints out the cordinates 'x' and 'y' from starting point 'left' to 'right'. Debug method
	private void printXY(int left, int right) {
		for(int i = left; i < right; i++) {
			System.out.println(x[i] + "," + y[i]);
		}
	}

	// Finner de statiske verdiene til x (min og maks) og y
	private void findStaticXY() {
		for(int i = 0; i < x.length; i++) {
            if(MAX_X < x[i]) {
                MAX_X = x[i];
            }
            if(MAX_Y < y[i]) {
                MAX_Y = y[i];
            }
            if(MIN_X > x[i]) {
                MIN_X = x[i];
            }
        }
	}

	// Klasse for å finne største x og y parallelt
	class Worker implements Runnable {
		int id, start, stop, localMaxX, localMaxY, localminx, localmaxx;
		public Worker(int id) {
			this.id = id;
			
			// Kalkulere start og stop
			int jump = x.length/numberOfCores;
			start = jump * id;
			if(id == numberOfCores-1){
				stop = x.length;
			} else {
				stop = (jump * id) + jump;
			}
		}

		public void run() {
			int tmp = Integer.MAX_VALUE;
			for(int i = start; i < stop; i++) {
				// Samme som "findStatiXY" og "findXY"
				if(localMaxX < x[i]) {
        	        localMaxX = x[i];
        	        localmaxx = i;
        	    }
        	    if(localMaxY < y[i]) {
        	        localMaxY = y[i];
        	    }
        	    if(tmp > x[i]) {
        	    	tmp = x[i];
        	    	localminx = i;
        	    }
			}
			localStaticX[id] = localMaxX;
			localStaticY[id] = localMaxY;
			threadMaxx[id] = localmaxx;
			threadMinx[id] = localminx;
			// Finished
			try{
				allThreads.await();
			} catch (Exception e) {}
		}
	}

	// Klasse for å finne innhyllingen parallelt. Deler seg opp i to tråder, en for over linja og en for under.
	class ParaSolution extends Thread {
		int id, level, p1, p2, p3;
		IntList coordniates, list, localCoHull;
		public ParaSolution(int id, int level, IntList coordniates, IntList list, int p1, int p2, int p3) {
			this.id = id;
			this.level = level;
			this.coordniates = coordniates;
			this.list = list;
			this.p3 = p3;
			this.p1 = p1;
			this.p2 = p2;
			localCoHull = new IntList();
		}

		public void run() {
			// if id = 0. Spesialtilfelle
			if(id == 0) {
				IntList coordniates = new IntList();
        		for(int i = 0; i < x.length; i++) {
            		coordniates.add(i);
        		}
        		// Punktene over
	       		list = getCorrespondingCoordinates(maxx, minx, coordniates);
		    	p3 = getOuterPoint(maxx, minx, list);
		    	ParaSolution right = new ParaSolution(id+1, level-1, coordniates, list, maxx, minx, p3);

		    	// Finne punktene under
		        list = getCorrespondingCoordinates(minx, maxx, coordniates);
		        p3 = getOuterPoint(minx, maxx, list);
		        ParaSolution left = new ParaSolution(id+2, level-1, coordniates, list, minx, maxx, p3);
		        right.start();
		        left.start();
		        try{
		        	left.join();
		        	right.join();
		        } catch (Exception e) {e.printStackTrace();}
		        // Add resultat til this.localCoHull
		        localCoHull.add(maxx);
		        for(int i = 0; i < right.localCoHull.size(); i++) {
		        	localCoHull.add(right.localCoHull.get(i));
		        }
		        localCoHull.add(minx);
		        for(int i = 0; i < left.localCoHull.size(); i++) {
		        	localCoHull.add(left.localCoHull.get(i));
		        }
		        // Sette localHull til globale verdien
		        paraHull = localCoHull;
		        try{
					para.await();
				} catch (Exception e){}
			}  else {
				sekvRek(p1, p2, p3, list, localCoHull);
			}
		}
	}
}