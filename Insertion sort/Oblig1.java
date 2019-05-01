import java.util.Random;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;
import java.text.DecimalFormat;
import java.math.RoundingMode;

// Assignment: http://www.uio.no/studier/emner/matnat/ifi/INF2440/v16/obliger/oblig1-finn40storste-2016.pdf

// Class with main
class Oblig1 {
	
	// One array for my implementation of A2 (range), one array for Arrays.sort (control), one for copying randomArray for looping and 
	// one for the parallel sorting.
	int[] range, control, paraArray;
	int speedUp;
	CyclicBarrier b;

 	public static void main(String[] args) {
 		new Oblig1().administrator();
	}

	// Call on the execution of the algorithms  with different arraysizes
	void administrator() {

 		// Test for 1000
 		System.out.println("Result for 1 000");
		// Fill all arrays with random numbers
 		sekSolution(1000);
 		
 		// Test for 10 000
 		System.out.println("\nResult for 10 000");
 		// Fill all arrays with random numbers
 		sekSolution(10000);

 		// Test for 10 000 000
 		System.out.println("\nResult for 10 000 000");
		// Fill all arrays with random numbers
 		sekSolution(10000000);

 		// Test for 100 000 000
 		System.out.println("\nResult for 100 000 000");
 		sekSolution(100000000);
	}

	// Administrating the seq solution
	void sekSolution(int size) {

		// Stores time for each individual run
		double[] myTimes = new double[9];
		double javaTimes = 0.0;
		double[] paraTimes = new double[9];
		double speedUp;
		long mySekv = 0;
		long javaSekv = 0;
		long paraNano = 0;
		int loop = 9;
		int numberOfCores = Runtime.getRuntime().availableProcessors();
		int jump;
		// Keep track of where each thread starts the sorting
		int[] paraStart = new int[numberOfCores];
		b = new CyclicBarrier(numberOfCores+1);

		for(int i = 0; i < loop; i++) {

			// Refill arrays with random ints
			fillArraysWithRandom(size);
			jump = paraArray.length / numberOfCores;

			// Start timer for my sequential implementation
			mySekv = System.nanoTime();
			earlySort(range, 0, 40);

			// Checks the rest of the array for bigger numbers	
			checkForBiggerNumber();
			// My sekv is done.
			myTimes[i] = ((System.nanoTime()-mySekv) / 1000000.0);

			// Check with javas default
			javaSekv = System.nanoTime();
			Arrays.sort(control);
			javaTimes = ((System.nanoTime()-javaSekv) / 1000000.0);

			// Start timer for my parallel implementation
			paraNano = System.nanoTime();

			// Start of paralell sorting
			for(int j = 0; j < numberOfCores; j++) {
				if(j == numberOfCores) {
					new Thread(new MultiSort(j, (jump*j)+1, ((jump*j)+jump))).start();
				} else if(j == 0) {
					new Thread(new MultiSort(j, jump*j, (jump*j)+jump)).start();
				}else {
					new Thread(new MultiSort(j, (jump*j)+1, (jump*j)+jump)).start();
				}
				paraStart[j] = jump*j;
			}
			// Wait for the other threads to finish
			try{
			   b.await();
			} catch (Exception e) {
			 	System.out.println("Something went wrong");
			 	return;
			}
			// Sequential sorting of the rest of the program
			sortRest(numberOfCores, paraStart);
			paraTimes[i] = ((System.nanoTime()-paraNano) / 1000000.0);

			// Checks if all arrays top 40 are the same for each run.
			System.out.println("Run: " + i + ". All same: " + ifCorrect());
		}

		// Sort time used for all algorithms
		Arrays.sort(myTimes);
		Arrays.sort(paraTimes);

		// Calculate speedup
		speedUp = myTimes[4] - paraTimes[4];
		DecimalFormat df = new DecimalFormat("0.#####");
    	df.setRoundingMode(RoundingMode.DOWN);
   		double outputNum = Double.valueOf(df.format(speedUp));
   		double speedUpFactor = Double.valueOf(df.format(myTimes[4]/paraTimes[4]));

		// Prints statistics
		System.out.println("mySeq: " + myTimes[4] + "ms\nArray.sort(): " + javaTimes + "ms\nparallel: " + paraTimes[4] + "ms");
		System.out.println("Speedup = " + outputNum + "ms, factor: " + speedUpFactor + " times quicker");
	}

	// Checks if all arrays top 40 numbers match. Return true if they do.
	boolean ifCorrect() {
		boolean bb = true;
		for(int k = 0; k < 40; k++) {
			if(range[k] != control[(control.length -1)- k] || paraArray[k] != control[(control.length -1)- k]) {
				bb = false;
			}
		}
		return bb;
	}

	// Sort the 40 first after the initial sort
	private void sortNew1(int[] a, int start, int end) {
		int tmp = 0;
		for(int i = end; i > start; i--) {
			if(a[i] > a[i-1]){
				tmp = a[i];
				a[i] = a[i-1];
				a[i-1] = tmp;
				// Stops the loop when we have looked at all interesting scenarios
			} else {
				return;
			}
		}
	}

	// Sort remaing part of 'paraArray' after all threads are done with the initial sort
	void sortRest(int numberOfCores, int[] paraStart) {
		int tmp, shifter;
		for(int i = 0; i < numberOfCores; i++) {
			for(int j = paraStart[i]; j < paraStart[i] + 40; j++) {
				if(paraArray[j] > paraArray[39]) {
					tmp = paraArray[j];
					paraArray[j] = paraArray[39];
					paraArray[39] = tmp;
					sortNew1(paraArray, 0, 39);
				}
			}
		}
	}

	// Fills both arrays with random numbers using Random from java.util
	void fillArraysWithRandom(int size) {

		range = new int[size];
		control = new int[size];
		paraArray = new int[size];
		int tmp = 0;
		Random rand = new Random(1337);
		for (int i = 0; i < size; i++) {
			tmp	= rand.nextInt(Integer.MAX_VALUE);
			range[i] = tmp;
			control[i] = tmp;
			paraArray[i] = tmp;
		}
	}

	// Checks rest of the subarray for higher numbers than top 40. 
	// If found the method replaces the the bigger number with the smallest of 
	// top 40, then calls on sortNew1 to sort the new number. 
	void checkForBiggerNumber() {
		int tmp = 0;
		for(int i = 40; i < range.length; i++) {
			if(range[i] > range[39]) {
				tmp = range[i];
				range[i] = range[39];
				range[39] = tmp;
				sortNew1(range, 0, 39);
			}
		}
	}

	// Same as above, only this is made for the parallell solution. 
	void checkForBiggerNumber(int[] arr, int s, int stop) {
		int tmp = 0;
		for(int i = s+40; i < stop; i++) {
			if(arr[i] > arr[s+39]) {
				tmp = arr[i];
				arr[i] = arr[s+39];
				arr[s+39] = tmp;	
				sortNew1(arr, s, s+39);
			}
		}
	}

	// Sorts the 40 first numbers in a descending insertion sort fashion. 
	// Mostly written by Arne Maus.
	void earlySort(int[] a, int v, int h) {
		int i, t;
		for(int k = v; k < h; k++) {
			// invariant: a[v..k] er nå sortert stigende (minste først)
			t = a[k+1];
			i = k;
			while (i >= v && a[i] < t) {
				a[i +1] = a[i];
				i--;
			}
			a[i+1] = t;
		} 
	}

	// Internal class for threads
	class MultiSort implements Runnable {

		int id;
		int start;
		int end;
		public MultiSort(int id, int start, int end) {
			this.id = id;
			this.start = start;
			this.end = end;
		}

		public void run() {
			// Sort 40 first in subarray
			earlySort(paraArray, start, (start + 40));
			// Check for bigger numbers in rest of the subarray
			checkForBiggerNumber(paraArray,start, end);

			try {
				b.await();																																																																																																																			
	        } catch (Exception e) {
	        	System.out.println("Something went wrong 2");
	        }
		}
	}
}
