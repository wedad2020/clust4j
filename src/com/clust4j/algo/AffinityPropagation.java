package com.clust4j.algo;

import java.util.ArrayList;
import java.util.Random;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.AbstractRealMatrix;
import org.apache.commons.math3.util.FastMath;

import com.clust4j.log.LogTimeFormatter;
import com.clust4j.log.Log.Tag.Algo;
import com.clust4j.utils.CentroidLearner;
import com.clust4j.utils.Classifier;
import com.clust4j.utils.ClustUtils;
import com.clust4j.utils.Convergeable;
import com.clust4j.utils.GeometricallySeparable;
import com.clust4j.utils.MatUtils;
import com.clust4j.utils.SelfSegmenting;
import com.clust4j.utils.SimilarityMetric;
import com.clust4j.utils.VecUtils;
import com.clust4j.utils.MatUtils.Axis;

public class AffinityPropagation extends AbstractClusterer implements Convergeable, Classifier, SelfSegmenting, CentroidLearner {
	/** The number of stagnant iterations after which the algorithm will declare convergence */
	final public static int DEF_ITER_BREAK = 15;
	final public static int DEF_MAX_ITER = 200;
	final public static double DEF_MIN_CHANGE = 0d;
	final public static double DEF_DAMPING = 0.5;
	final public static boolean DEF_ADD_GAUSSIAN_NOISE = true;
	
	
	/** Damping factor */
	private final double damping;
	
	/** Remove degeneracies with noise? */
	private final boolean addNoise;
	
	/** Number of stagnant iters after which to break */
	private final int iterBreak;
	
	/** The max iterations */
	private final int maxIter;

	/** Num rows, cols */
	private final int m;
	
	/** Min change convergence criteria */
	private final double minChange;
	
	/** Class labels */
	private volatile int[] labels = null;
	
	/** Track convergence */
	private volatile boolean converged = false;
	
	/** Number of identified clusters */
	private volatile int numClusters;
	
	/** Count iterations */
	private volatile int iterCt = 0;
	
	/** Sim matrix. Only use during fitting, then back to null to save space */
	private volatile double[][] sim_mat = null;
	
	/** Holds the centroids */
	private volatile ArrayList<double[]> centroids = null;
	
	/** Holds centroid indices */
	private volatile ArrayList<Integer> centroidIndices = null;
	
	
	
	
	public AffinityPropagation(final AbstractRealMatrix data) {
		this(data, new AffinityPropagationPlanner());
	}
	
	public AffinityPropagation(final AbstractRealMatrix data, final AffinityPropagationPlanner planner) {
		super(data, planner);
		String error;
		
		
		// Check some args
		if(planner.damping < DEF_DAMPING || planner.damping >= 1) {
			error = "damping must be between " + DEF_DAMPING + " and 1";
			if(verbose) error(error);
			throw new IllegalArgumentException(error);
		}
		
		this.damping = planner.damping;
		this.iterBreak = planner.iterBreak;
		this.m = data.getRowDimension();
		this.minChange = planner.minChange;
		this.maxIter = planner.maxIter;
		this.addNoise = planner.addNoise;
		
		
		if(verbose) {
			meta("damping="+damping);
			meta("maxIter="+maxIter);
			meta("minChange="+minChange);
			meta("addNoise="+addNoise);
			
			if(!addNoise) warn("not scaling with Gaussian noise can cause the algorithm not to converge");
		}
	}
	
	
	
	
	public static class AffinityPropagationPlanner extends AbstractClusterer.BaseClustererPlanner {
		private int maxIter = DEF_MAX_ITER;
		private double minChange = DEF_MIN_CHANGE;
		private int iterBreak = DEF_ITER_BREAK;
		
		private double damping = DEF_DAMPING;
		private boolean scale = DEF_SCALE;
		private Random seed = DEF_SEED;
		private GeometricallySeparable dist	= DEF_DIST;
		private boolean verbose	= DEF_VERBOSE;
		private boolean addNoise = DEF_ADD_GAUSSIAN_NOISE;

		public AffinityPropagationPlanner() { /* Default constructor */ }
		
		public AffinityPropagationPlanner addGaussianNoise(boolean b) {
			this.addNoise = b;
			return this;
		}
		
		@Override
		public GeometricallySeparable getSep() {
			return dist;
		}

		@Override
		public boolean getScale() {
			return scale;
		}

		@Override
		public Random getSeed() {
			return seed;
		}

		@Override
		public boolean getVerbose() {
			return verbose;
		}
		
		public AffinityPropagationPlanner setDampingFactor(final double damp) {
			this.damping = damp;
			return this;
		}
		
		public AffinityPropagationPlanner setIterBreak(final int iters) {
			this.iterBreak = iters;
			return this;
		}
		
		public AffinityPropagationPlanner setMaxIter(final int max) {
			this.maxIter = max;
			return this;
		}
		
		public AffinityPropagationPlanner setMinChange(final double min) {
			this.minChange = min;
			return this;
		}

		@Override
		public AffinityPropagationPlanner setScale(boolean b) {
			scale = b;
			return this;
		}

		@Override
		public AffinityPropagationPlanner setSeed(Random rand) {
			seed = rand;
			return this;
		}

		@Override
		public AffinityPropagationPlanner setVerbose(boolean b) {
			verbose = b;
			return this;
		}

		@Override
		public AffinityPropagationPlanner setSep(GeometricallySeparable dist) {
			this.dist = dist;
			return this;
		}
	}




	@Override
	public int[] getLabels() {
		return VecUtils.copy(labels);
	}

	@Override
	public boolean didConverge() {
		return converged;
	}

	@Override
	public int getMaxIter() {
		return maxIter;
	}

	@Override
	public double getMinChange() {
		return minChange;
	}

	@Override
	public int itersElapsed() {
		return iterCt;
	}

	@Override
	public String getName() {
		return "AffinityPropagation";
	}

	@Override
	public Algo getLoggerTag() {
		return com.clust4j.log.Log.Tag.Algo.AFFINITY_PROP;
	}

	@Override
	public AffinityPropagation fit() {
		synchronized(this) {
			
			if(null != labels)
				return this;
			
			
			
			// Init labels
			final long start = System.currentTimeMillis();
			labels = new int[m];
			String error;
			
			
			
			// Calc sim mat to MAXIMIZE VALS
			final long sim_time = System.currentTimeMillis();
			if(getSeparabilityMetric() instanceof SimilarityMetric) {
				if(verbose) info("computing similarity matrix");
				sim_mat = ClustUtils.similarityFullMatrix(data, (SimilarityMetric)getSeparabilityMetric());
			} else {
				if(verbose) info("computing negative distance (pseudo similarity) matrix");
				sim_mat = MatUtils.negative(ClustUtils.distanceFullMatrix(data, getSeparabilityMetric()));
			}
			if(verbose) info("completed similarity computations in " + LogTimeFormatter.millis(System.currentTimeMillis()-sim_time, false));
			
			
			
			// Extract the upper triangular portion from sim mat, get the median as default pref 
			if(verbose) info("computing initialization point");
			int idx = 0, mChoose2 = ((m*m) - m) / 2;
			final double[] vals = new double[mChoose2];
			for(int i = 0; i < m - 1; i++)
				for(int j = i + 1; j < m; j++)
					vals[idx++] = sim_mat[i][j];
			
			final double pref = VecUtils.median(vals);
			if(verbose) info("pref = "+pref);
			
			// Place pref on diagonal of sim mat
			if(verbose) info("refactoring similarity matrix diagonal vector");
			for(int i = 0; i < m; i++)
				sim_mat[i][i] = pref;
			
			
			
			// Affinity propagation uses two matrices: the responsibility 
			// matrix, R, and the availability matrix, A
			double[][] A = new double[m][m];
			double[][] R = new double[m][m];
			double[][] tmp; // Intermediate staging...
			
			
			if(addNoise) {
				// Add some extremely small noise to the similarity matrix
				double[][] tiny_scaled = MatUtils.scalarMultiply(sim_mat, MatUtils.EPS);
				tiny_scaled = MatUtils.scalarAdd(tiny_scaled, MatUtils.TINY*100);
				
				if(verbose) info("removing matrix degeneracies; scaling with minute Gaussian noise");
				long gausStart = System.currentTimeMillis();
				double[][] noise = MatUtils.randomGaussian(m, m, getSeed());
				if(verbose) info("Gaussian noise matrix computed in " + 
						LogTimeFormatter.millis(System.currentTimeMillis()-gausStart, false));
				double[][] noiseMatrix = null;
				
				
				try {
					long multStart = System.currentTimeMillis();
					if(verbose) info("multiplying scaling matrix by noise matrix ("+m+"x"+m+")");
					noiseMatrix = MatUtils.multiply(tiny_scaled, noise);
					if(verbose) info("matrix product computed in " + 
							LogTimeFormatter.millis(System.currentTimeMillis()-multStart, false));
				} catch(DimensionMismatchException e) {
					error = e.getMessage();
					if(verbose) error(error);
					throw new InternalError("similarity matrix produced DimMismatch: "+ error); // Should NEVER happen
				}
				
				sim_mat = MatUtils.add(sim_mat, noiseMatrix);
			}
			
			
			// Begin here
			int[] I = null;
			double[][] e = new double[m][iterBreak];
			double[] Y;		// vector of arg maxes
			double[] Y2;	// vector of maxes post neg inf
			double[] sum_e;
			
			for(iterCt = 0; iterCt < maxIter; iterCt++) {
				if(verbose)
					trace("beginning iteration " + (iterCt+1));
				
				tmp = MatUtils.add(A, sim_mat);
				
				// Get indices of ROW max
				I = MatUtils.argMax(tmp, Axis.ROW);
				
				// Vector of arg maxes
				Y = new double[m];
				for(int i = 0; i < m; i++) {
					Y[i] = tmp[i][I[i]]; // Grab the current val
					tmp[i][I[i]] = Double.NEGATIVE_INFINITY; // Set that idx to neg inf now
				}
				
				
				// Get new max vector
				Y2 = MatUtils.max(tmp, Axis.ROW);
				double[][] YM = MatUtils.fromVector(Y, m, Axis.ROW);
				tmp = MatUtils.subtract(sim_mat, YM);
				
				int ind = 0;
				for(int j: I) 
					tmp[ind][j] = sim_mat[ind][j] - Y2[ind++];
				
				
				// Damping
				tmp	= MatUtils.scalarMultiply(tmp, 1 - damping);
				R	= MatUtils.scalarMultiply(R, damping);
				R	= MatUtils.add(R, tmp);
				
				
				// Compute availability -- start by setting anything less than 0 to 0 in tmp:
				for(int i = 0; i < m; i++) {
					for(int j = 0; j < m; j++)
						tmp[i][j] = FastMath.max(R[i][j], 0);
					tmp[i][i] = R[i][i]; // Set diagonal elements in tmp equal to those in R
				}
				
				
				// Get column sums, transform to matrix, subtract from tmp
				final double[][] colSumsMat = MatUtils.fromVector(MatUtils.colSums(tmp),m,Axis.COL);
				tmp = MatUtils.subtract(tmp, colSumsMat);
				
				// Set any negative values to zero but keep diagonal at original
				for(int i = 0; i < m; i++) {
					for(int j = 0; j < m; j++) {
						if(i == j)
							continue;
						else if(tmp[i][j] < 0)
							tmp[i][j] = 0;
					}
				}
				
				
				// More damping
				tmp	= MatUtils.scalarMultiply(tmp, 1 - damping);
				A	= MatUtils.scalarMultiply(A, damping);
				A	= MatUtils.subtract(A, tmp);
				
				
				// Check convergence criteria ----------------------
				final double[] diagA = MatUtils.diagFromSquare(A);
				final double[] diagR = MatUtils.diagFromSquare(R);
				final double[] mask = new double[diagA.length];
				for(int i = 0; i < mask.length; i++)
					mask[i] = diagA[i] + diagR[i] > 0 ? 1d : 0d;
					
				// Set the mask in `e`
				MatUtils.setColumnInPlace(e, iterCt % iterBreak, mask);
				
				// Get k
				numClusters = (int)VecUtils.sum(mask);

				if(iterCt >= iterBreak) {
					sum_e = MatUtils.rowSums(e);
					
					// Create bool_mask
					int maskCt = 0;
					for(int i = 0; i < sum_e.length; i++)
						maskCt += sum_e[i] == 0 || sum_e[i] == iterBreak ? 1 : 0;
					
					converged = maskCt == m;
					
					if((converged && numClusters > 0) || iterCt == maxIter) {
						if(verbose) info("converged after " + (iterCt++) + " iteration"+(iterCt!=1?"s":""));
						break;
					}
				} // End outer if
				
				if(verbose) trace("iter " + (iterCt) + ": algorithm has not reached convergence");
			} // End for

			
			if(!converged && verbose)
				warn("algorithm did not converge");
			if(verbose) info("labeling clusters from availability and responsibility matrices");
			
			
			// sklearn line: I = np.where(np.diag(A + R) > 0)[0]
			final ArrayList<Integer> arWhereOver0 = new ArrayList<>();
			double[] ar = MatUtils.diagFromSquare(MatUtils.add(A, R));
			
			for(int i = 0; i < ar.length; i++)
				if(ar[i] > 0)
					arWhereOver0.add(i);
			
			I = new int[arWhereOver0.size()];
			for(int j = 0; j < I.length; j++) I[j] = arWhereOver0.get(j);
			
			
			
			
			// Assign final K -- sklearn line: K = I.size  # Identify exemplars
			numClusters = I.length;
			if(verbose) info(numClusters+" cluster" + (numClusters!=1?"s":"") + " identified");
			
			
			
			// Assign the labels
			if(numClusters > 0) {
				
				/*
				 * I holds the columns we want out of sim_mat,
				 * retrieve this cols, do a row-wise argmax to get 'c'
				 * sklearn line: c = np.argmax(S[:, I], axis=1)
				 */
				double[][] over0cols = new double[m][numClusters];
				int over_idx = 0;
				for(int i: I)
					MatUtils.setColumnInPlace(over0cols, over_idx++, MatUtils.getColumn(sim_mat, i));

				
				
				/*
				 * Identify clusters
				 * sklearn line: c[I] = np.arange(K)  # Identify clusters
				 */
				int[] c = MatUtils.argMax(over0cols, Axis.ROW);
				int k = 0;
				for(int i: I)
					c[i] = k++;
				
				
				/* Refine the final set of exemplars and clusters and return results
				 * sklearn:
				 * 
				 *  for k in range(K):
			     *      ii = np.where(c == k)[0]
			     *      j = np.argmax(np.sum(S[ii[:, np.newaxis], ii], axis=0))
			     *      I[k] = ii[j]
				 */
				ArrayList<Integer> ii = null;
				int[] iii = null;
				for(k = 0; k < numClusters; k++) {
					// indices where c == k; sklearn line: 
					// ii = np.where(c == k)[0]
					ii = new ArrayList<Integer>();
					for(int u = 0; u < c.length; u++)
						if(c[u] == k)
							ii.add(u);
					
					// Big block to break down sklearn process
					// overall sklearn line: j = np.argmax(np.sum(S[ii[:, np.newaxis], ii], axis=0))
					iii = new int[ii.size()]; // convert to int array for MatUtils
					for(int j = 0; j < iii.length; j++) iii[j] = ii.get(j);
					
					
					// sklearn line: S[ii[:, np.newaxis], ii]
					double[][] cube = MatUtils.getRows(MatUtils.getColumns(sim_mat, iii), iii);
					double[] colSums = MatUtils.colSums(cube);
					final int argMax = VecUtils.argMax(colSums);
					
					
					// sklearn: I[k] = ii[j]
					I[k] = iii[argMax];
				}
				
				
				// sklearn line: c = np.argmax(S[:, I], axis=1)
				double[][] colCube = MatUtils.getColumns(sim_mat, I);
				c = MatUtils.argMax(colCube, Axis.ROW);
				
				
				// sklearn line: c[I] = np.arange(K)
				for(int j = 0; j < I.length; j++) // I.length == K, == numClusters
					c[I[j]] = j;
				
				
				// sklearn line: labels = I[c]
				for(int j = 0; j < m; j++)
					labels[j] = I[c[j]];
				
				
				/* 
				 * Reduce labels to a sorted, gapless, list
				 * sklearn line: cluster_centers_indices = np.unique(labels)
				 */
				centroidIndices = new ArrayList<Integer>(numClusters);
				for(Integer i: labels) // force autobox
					if(!centroidIndices.contains(i)) // Not race condition because synchronized
						centroidIndices.add(i);
				
				/*
				 * final label assignment...
				 * sklearn line: labels = np.searchsorted(cluster_centers_indices, labels)
				 */
				for(int i = 0; i < labels.length; i++)
					labels[i] = centroidIndices.indexOf(labels[i]);
				
			} else {
				centroids = new ArrayList<>(); // Empty
				centroidIndices = new ArrayList<>(); // Empty
				for(int i = 0; i < m; i++)
					labels[i] = -1; // Missing
			}
			
			
			if(verbose)
				info("model " + getKey() + " completed in " + 
					LogTimeFormatter.millis(System.currentTimeMillis()-start, false));
			
			
			// Clean up
			sim_mat = null;
			return this;
			
		} // End synch
		
	} // End fit
	
	@Override
	public int getNumberOfIdentifiedClusters() {
		return numClusters;
	}

	@Override
	public ArrayList<double[]> getCentroids() {
		return centroids;
	}
	
	public ArrayList<Integer> getCentroidIndices() {
		return centroidIndices;
	}
}