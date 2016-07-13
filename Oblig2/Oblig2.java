import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;

// Assignment: http://www.uio.no/studier/emner/matnat/ifi/INF2440/v16/obliger/oblig2ver2-2016.pdf

class Oblig2 {

	public static void main(String[] args) {
		if(args.length != 1) {
			System.out.println("Usage: java Oblig2 <Max number>");
			return;
		}
		if(Integer.parseInt(args[0]) < 100)	 {
			System.out.println("Please use a number > 100");
			return;
		}
		new Oblig2().admin(Integer.parseInt(args[0]));
	}

	private void admin(int maxNum){
		// Sequential part
		EratosthenesSil seq = new EratosthenesSil(maxNum, false, null);
		EratosthenesSil para = new EratosthenesSil(maxNum, true, seq);
		if(Arrays.equals(seq.bitArr, para.bitArr)) {
			System.out.println("Both EratosthenesSil is the same: " +true);
		} else 
			System.out.println("Both EratosthenesSil is the same: " + false);
		// Print out matches and mismatches of para and seq sieve.
		/*for(int i = 0; i < seq.bitArr.length; i++) {
			if(seq.bitArr[i] != para.bitArr[i]) {
				System.out.println(i);
				System.out.println(String.format("%8s", Integer.toBinaryString(seq.bitArr[i] & 0xFF)).replace(' ', '0'));
				System.out.println(String.format("%8s", Integer.toBinaryString(para.bitArr[i] & 0xFF)).replace(' ', '0'));
			} else {
				System.out.println();
				System.out.println(String.format("%8s", Integer.toBinaryString(seq.bitArr[i] & 0xFF)).replace(' ', '0'));
				System.out.println(String.format("%8s", Integer.toBinaryString(para.bitArr[i] & 0xFF)).replace(' ', '0'));
			}
		}*/
	}
}

///--------------------------------------------------------
//
//     File: EratosthenesSil.java for INF2440-2016
//     implements bit-array (Boolean) for prime numbers
//     precode written by:  Arne Maus , Univ of Oslo,
//
//--------------------------------------------------------

/**
* Implements the bitArray of length 'maxNum' [0..maxNum/16 ]
*   1 - true (is prime number)
*   0 - false
*  can be used up to 2 G Bits (integer range)
*  16 numbers, i.e. 8 odd numbers per byte (bitArr[0] represents 1,3,5,7,9,11,13,15 )
*
*/
class EratosthenesSil {
	byte [] bitArr;           // bitArr[0] represents the 8 integers:  1,3,5,...,15, and so on
	int  maxNum;               // all primes in this bit-array is <= maxNum
	final  int [] bitMask = {1,2,4,8,16,32,64,128};  // kanskje trenger du denne
	final  int [] bitMask2 ={255-1,255-2,255-4,255-8,255-16,255-32,255-64, 255-128}; // kanskje trenger du denne
	ArrayList<ArrayList> factorized;
	ArrayList<Long> tmpFac;
	// Used to store the result of each thread
	ArrayList<byte[]> res;	
	ArrayList<Integer> primeNumbers;
	CyclicBarrier cb;	
	private final ReentrantLock lock;
	EratosthenesSil es;
	int cores, root;
	boolean foundAllParaFac;
	double eraTime, factTime, paraEraTime, paraFactTime;

	@SuppressWarnings("unchecked")
	public EratosthenesSil (int maxNum, boolean b, EratosthenesSil es) {
		this.es = es;
		this.maxNum = maxNum;
		long time;
		lock = new ReentrantLock();
		// Eratosthenes sil
	    // 1 er ikke et primtall
		// Sequential
		if(!b) {
			bitArr = new byte [(maxNum/16)+1];
			setAllPrime(bitArr);
			crossOut(0);
			// Generate primes sequenceually
			time = System.nanoTime();
			generatePrimesByEratosthenes(3, maxNum);
			eraTime = (System.nanoTime() - time) / 1000000.0;
			
			// System.out.println(String.format("%8s", Integer.toBinaryString(bitArr[i] & 0xFF)).replace(' ', '0'));

			// Factorization of 100 lagerst numbers in maxNum
			factorized = new ArrayList<ArrayList>();
			time = System.nanoTime();
			for(int i = 0; i < 100; i++) {
				factorized.add(factorize(maxNum-i));
			}
			factTime = (System.nanoTime() - time) / 1000000.0;
			printStats(eraTime, factTime, "Sequential");
		// Parallell
		} else {
			// Initialize bitArr
			bitArr = new byte [(maxNum/16)+1];
			setAllPrime(bitArr);

			// Other initializing/declarations
			cores = Runtime.getRuntime().availableProcessors();
			cb = new CyclicBarrier(cores+1);
			Thread[] t = new Thread[cores];
			root = (int) Math.sqrt(maxNum)+1;
			res = new ArrayList<byte[]>();
			primeNumbers = new ArrayList<Integer>();

			// Start timer and calculate all primes up to square of maxNum
			time = System.nanoTime();
			calcSquareFirstToSquareRoot();

			int jump = bitArr.length/cores;
			// Start threads
			for(int i = 0; i < cores; i++) {
				if(i == cores -1) {
					(t[i] = new Thread(new Worker(i, jump*i, bitArr.length))).start();	
				} else {
					(t[i] = new Thread(new Worker(i, jump*i, (jump*i)+jump))).start();
				}
			}

			try{
				cb.await();

			} catch (Exception e) {
				System.out.println("Error!!!! CyclicBarrier in ErathostenesSil (main) went wrong!");
			}

			paraEraTime = (System.nanoTime() - time) / 1000000.0;

			// Parallel factorization
			factorized = new ArrayList<ArrayList>();
			time = System.nanoTime();
			for(int j = 0; j < 100; j++) {
				int helper = maxNum/cores;
				tmpFac = new ArrayList<Long>();
				foundAllParaFac = false;
				for(int i = 0; i < cores; i++) {
					if(i == cores -1) {
						(t[i] = new Thread(new ParaFact(i, helper*i, maxNum, maxNum-j))).start();	
					} else {
						(t[i] = new Thread(new ParaFact(i, helper*i, (helper*i)+helper, maxNum-j))).start();
					}
				}
				try {
					for(int i = 0; i < cores; i++) t[i].join();
					factorized.add(tmpFac);
				} catch (Exception e) {
					System.out.println("Something wrong with para fact");
				}
			}
			factTime = (System.nanoTime() - time) / 1000000.0;
			printStats(paraEraTime, factTime, "Parallel");
		}

		//printBitArr();
    } // end konstruktor ErathostenesSil

    private void printFactNumbers() {
    	// The lager numbers
    	for(int i = 0; i < 5; i++) {
    		String s = Integer.toString(maxNum-i);
    		ArrayList temp = factorized.get(i);    		
    		for(int j = 0; j < temp.size(); j++) {
    			s +=" " + String.valueOf(temp.get(j));
    		} 
    		System.out.println(s);
    	}
    	System.out.println("(...)");
    	// The smaller numbers
    	for(int i = 4; i >= 0; i--) {
    		String s = Integer.toString((maxNum-99)+i);

    		ArrayList temp = factorized.get(factorized.size()-i-1);    		
    		for(int j = 0; j < temp.size(); j++) {
    			s +=" " + String.valueOf(temp.get(j));
    		} 
    		System.out.println(s);
    	}
    }

    // Calculates all primes up to the square root of M
    private void calcSquareFirstToSquareRoot() {
   		int num = 0;

        for(int i = 3; i <= root; i = i+2)  { 		  	  
        	int counter = 0; 	  
        	for(num = i; num >= 1; num--){
        	    if(i % num == 0) {
 					counter = counter + 1;
	    		}
	  		}
	  		if (counter == 2) {
	     		primeNumbers.add(i);
	  		}	
       	}	
    }

    // Adds dummy values to res to avoid a rather strange error
    private void fixRes() {
    	for(int i = 0; i < bitArr.length; i++) {
    		res.add(i, null);
    	}
    }

    // prints out the bit value of every index in bitArr
    private void printBitArr() {
    	for(int i = 0; i < bitArr.length; i++) {
    		System.out.println(String.format("%8s", Integer.toBinaryString(bitArr[i] & 0xFF)).replace(' ', '0'));
    	}
    }

    // Prints statics over runt time
    private void printStats(double eraTime, double factTime, String s) {
		System.out.println("\n" + s + " part:\nTime used for EratosthenesSil: " + eraTime + "ms");
		System.out.println("Time used to factorized 100 largest numbers: " + factTime + "ms. All correct: " + checkFactorization());
		if(es != null) {
			System.out.println("Sppedup Erathostenes Sil: " + es.eraTime/eraTime);
			System.out.println("Sppedup faktorisering: " + es.factTime/factTime);
		}
		printFactNumbers();
    }
    // Checks if the factorization is correct by multiply all found factors of a number. if(number == factor * factor...)
    private boolean checkFactorization() {
     	long correct = 0, check = 0;
    	for(int i = 0; i < factorized.size(); i++) {
			correct = maxNum - i;
			check = 1;
			for(int j = 0; j < factorized.get(i).size(); j++) {
				//if(factorized.get(i).get(j) != null)
				check = check * (long) factorized.get(i).get(j);
			}
			if(correct != check) {
				return false;
			}
		}
		return true;
    }

    // Prints n-> n-100 numbers and their factors.
    private void print100fact() {

    	for(int i = 0; i < factorized.size(); i++) {
			System.out.println("\nFactors for " + (maxNum - i) + ": ");
			for(int j = 0; j < factorized.get(i).size(); j++) {
				System.out.print(" " + factorized.get(i).get(j));
			}
		}
    }

    // Sets all indexes to 11111111
    void setAllPrime(byte[] b) {
    	for (int i = 0; i < b.length; i++) {
    		b[i] = (byte)255; // Sets all to 1/true
    	}
    }

    // Changes 1 to 0 on a given index
    void crossOut(int i) {
    	// set as not prime- cross out (set to 0)  bit represening 'int i'
    	// Find byte
		int index = (int) (i >>> 4);
		// Find bit and set to 0
    	bitArr[index] ^= (-0 ^ bitArr[index]) & (1 << ((i&15)>>1));
	} 

	// Checks if a number is a prime number
	boolean isPrime (int i) {
		if(i == 2) {
			return true;
		// Even number
		} else if(i%2 == 0) {
			return false;
		}
		int index = (int) (i >>> 4);
		if(((bitArr[index] >> ((i&15)>>1)) & 1) == 1){
			return true;
		} 
		return false;
	}

	// Factorization
	ArrayList<Long> factorize (long num) {
		ArrayList<Long> fakt = new ArrayList<Long>();
		int i;
		for(i = 2; i < num; i++) {
			if(isPrime(i) && num % i == 0) {
				fakt.add((long) i);
				num /= i;
				i--;
			}
		}
		fakt.add((long) i);
		return fakt;
	} // end factorize


	int nextPrime(int i) {
		// returns next prime number after number 'i'
		// <din kode her>
		return  i;
	} // end nextPrime

	// Prints all prime numbers <= maxnum
	void printAllPrimes(){
		for ( int i = 2; i <= maxNum; i++)
			if (isPrime(i)) System.out.println(" "+i);
	}

	// Generate primes with Eratosthenes sieve.
	void generatePrimesByEratosthenes(int start, int stop) {

		for(int i = start; i <= stop; i += 2) {
			// All found
			if(i * i > maxNum)
				return;
			// For parallell implementation
			if(i % 2 == 0) {
				i++;
			}
			int j = i*i;
			int r = i;
			while(j <= maxNum) {
				// Check for uneven numbers			
				if(j % 2 != 0) {
					crossOut(j);
				} 
				r++;
				j = i*r;
			}
		}
	} 	// end generatePrimesByEratosthenes

	// Generate primes prallelly.
	void paraEraGenerator(int start, int stop) {

		for (int i : primeNumbers) {	
			if(i * i > maxNum)
				return;

			int j = i*i;
			int r = i;

			if(j < start) {
				r = start / i+1;
				j = i*r;
			}

			while(j <= stop) {
				// Check for uneven numbers			
				if(j % 2 != 0) {
					crossOut(j);
				} 
				r++;
				j = i*r;
			}	
		}
	}

	synchronized void addTmpFac(int i, int num) {
		tmpFac.add((long)i);
		if(chechIfFinished(num)){
			foundAllParaFac = true;
		}
	}

	boolean chechIfFinished(int num) {
		long res = 1;
		for(int i = 0; i < tmpFac.size(); i++) {
			res = res*tmpFac.get(i);
		}
		if(res == num) return true;
		return false;
	}


	class Worker implements Runnable {
		// Uses start and stop for Eratosthenes Sieve
		int id, start, stop, end;

		public Worker(int id, int start, int stop) {
			this.id = id;
			this.start = start;
			this.stop = stop;
			end = (stop*16)+15;
			start = start * 16;
			if(id==0) crossOut(0);
			if(start != 0) start++;
			if(end>maxNum) end = maxNum;
			//System.out.println(start + " " + stop + " " + id);
		}

		public void run() {
			paraEraGenerator(start, end);
			//System.out.println(String.format("%8s", Integer.toBinaryString(localBitArr[0] & 0xFF)).replace(' ', '0'));	
			//res.add(id, localBitArr);
			try {
				cb.await();
			} catch (Exception e) {
				System.out.println("Error!!!! CyclicBarrier in ErathostenesSil (" + id + ") went wrong!");
			}
		}
	}

	class ParaFact implements Runnable {
		int id, start, stop, num;
		final int finalNum;

		public ParaFact(int id, int start, int stop, int num) {
			this.id = id;
			this.start = start;
			this.stop = stop;
			this.num = num;
			finalNum = num;
			if(start == 0) start = 2;
			if(id != 0 && start % 2 == 0) start++;
			//System.out.println(start + " " + stop);
		}

		public void run() {
			for(int i = start; i < stop; i++) {
				if(isPrime(i) && num % i == 0) {
					addTmpFac(i, finalNum);
					num/=i;
					i--;
				}
				if(foundAllParaFac){
					return;}
			}
		}
	}
} // end class EratosthenesSil
