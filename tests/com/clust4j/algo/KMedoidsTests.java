package com.clust4j.algo;

import static com.clust4j.TestSuite.getRandom;
import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.util.FastMath;
import org.junit.Test;

import com.clust4j.TestSuite;
import com.clust4j.algo.KMedoids.KMedoidsPlanner;
import com.clust4j.data.ExampleDataSets;
import com.clust4j.kernel.HyperbolicTangentKernel;
import com.clust4j.kernel.Kernel;
import com.clust4j.kernel.LaplacianKernel;
import com.clust4j.utils.Distance;

public class KMedoidsTests implements ClusterTest, ClassifierTest, ConvergeableTest {
	final Array2DRowRealMatrix data_ = ExampleDataSets.IRIS.getData();
	
	/**
	 * This is the method as it is used in the KMedoids class,
	 * except that the distance matrix is passed in
	 * @param indices
	 * @param med_idx
	 * @return
	 */
	protected static double getCost(ArrayList<Integer> indices, final int med_idx, final double[][] dist_mat) {
		double cost = 0;
		for(Integer idx: indices)
			cost += dist_mat[FastMath.min(idx, med_idx)][FastMath.max(idx, med_idx)];
		return cost;
	}
	
	
	@Test
	public void test() {
		final double[][] distanceMatrix = new double[][] {
			new double[]{0,1,2,3},
			new double[]{0,0,1,2},
			new double[]{0,0,0,1},
			new double[]{0,0,0,0}
		};
		
		final int med_idx = 2;
		
		final ArrayList<Integer> belonging = new ArrayList<Integer>();
		belonging.add(0); belonging.add(1); belonging.add(2); belonging.add(3);
		assertTrue(getCost(belonging, med_idx, distanceMatrix) == 4);
	}

	@Test
	@Override
	public void testItersElapsed() {
		assertTrue(new KMedoids(data_).fit().itersElapsed() > 0);
	}


	@Test
	@Override
	public void testConverged() {
		assertTrue(new KMedoids(data_).fit().didConverge());
	}


	@Test
	@Override
	public void testScoring() {
		new KMedoids(data_).fit().silhouetteScore();
	}


	@Test
	@Override
	public void testDefConst() {
		new KMedoids(data_);
	}


	@Test
	@Override
	public void testArgConst() {
		new KMedoids(data_, 3);
	}


	@Test
	@Override
	public void testPlannerConst() {
		new KMedoids(data_, new KMedoidsPlanner());
		new KMedoids(data_, new KMedoidsPlanner(3));
	}


	@Test
	@Override
	public void testFit() {
		new KMedoids(data_, new KMedoidsPlanner()).fit();
		new KMedoids(data_, new KMedoidsPlanner(3)).fit();
	}


	@Test
	@Override
	public void testFromPlanner() {
		new KMedoidsPlanner().buildNewModelInstance(data_);
		new KMedoidsPlanner(3).buildNewModelInstance(data_);
	}

	/** Scale = false */
	@Test
	public void KMedoidsTest1() {
		final double[][] data = new double[][] {
			new double[] {0.005, 	 0.182751,  0.1284},
			new double[] {3.65816,   0.29518,   2.123316},
			new double[] {4.1234,    0.27395,   1.8900002}
		};
		
		final Array2DRowRealMatrix mat = new Array2DRowRealMatrix(data);
		KMedoids km = new KMedoids(mat, 2);
		assertTrue(km.getSeparabilityMetric().equals(Distance.MANHATTAN));
		
		km.fit();

		assertTrue(km.getLabels()[0] == 0 && km.getLabels()[1] == 1);
		assertTrue(km.getLabels()[1] == km.getLabels()[2]);
		assertTrue(km.didConverge());
		//km.info("testing the k-medoids logger");
	}
	
	/** Scale = true */
	@Test
	public void KMedoidsTest2() {
		final double[][] data = new double[][] {
			new double[] {0.005, 	 0.182751,  0.1284},
			new double[] {3.65816,   0.29518,   2.123316},
			new double[] {4.1234,    0.27395,   1.8900002},
			new double[] {0.015, 	 0.161352,  0.1173},
		};
		
		final Array2DRowRealMatrix mat = new Array2DRowRealMatrix(data);
		KMedoids km = new KMedoids(mat, 
				new KMedoidsPlanner(2)
					.setScale(true)
					.setVerbose(true));
		km.fit();

		assertTrue(km.getLabels()[0] == 0 && km.getLabels()[1] == 1 && km.getLabels()[3] == 0);
		assertTrue(km.getLabels()[1] == km.getLabels()[2]);
		assertTrue(km.getLabels()[0] == km.getLabels()[3]);
		assertTrue(km.didConverge());
	}
	
	/** Now scale = false and multiclass */
	@Test
	public void KMedoidsTest3() {
		final double[][] data = new double[][] {
			new double[] {0.005, 	 0.182751,  0.1284},
			new double[] {3.65816,   0.29518,   2.123316},
			new double[] {4.1234,    0.0001,    1.8900002},
			new double[] {100,       200,       100}
		};
		
		final Array2DRowRealMatrix mat = new Array2DRowRealMatrix(data);
		KMedoids km = new KMedoids(mat, 
				new KMedoidsPlanner(3)
					.setScale(false)
					.setVerbose(true));
		km.fit();

		assertTrue(km.getLabels()[0] == 0 && km.getLabels()[1] == 1 && km.getLabels()[3] == 2);
		assertTrue(km.getLabels()[1] == km.getLabels()[2]);
		assertTrue(km.getLabels()[0] != km.getLabels()[3]);
		assertTrue(km.didConverge());
	}
	
	/** Now scale = true and multiclass */
	@Test
	public void KMedoidsTest4() {
		final double[][] data = new double[][] {
			new double[] {0.005, 	 0.182751,  0.1284},
			new double[] {3.65816,   0.29518,   2.123316},
			new double[] {4.1234,    0.2801,    1.8900002},
			new double[] {100,       200,       100}
		};
		
		final Array2DRowRealMatrix mat = new Array2DRowRealMatrix(data);
		KMedoids km = new KMedoids(mat, new KMedoidsPlanner(3).setScale(true));
		km.fit();
		
		assertTrue(km.getLabels()[1] == km.getLabels()[2]);
		assertTrue(km.getLabels()[0] != km.getLabels()[3]);
		assertTrue(km.didConverge());
	}
	
	// What if k = 1??
	@Test
	public void KMedoidsTest5() {
		final double[][] data = new double[][] {
			new double[] {0.005, 	 0.182751,  0.1284},
			new double[] {3.65816,   0.29518,   2.123316},
			new double[] {4.1234,    0.0001,    1.8900002},
			new double[] {100,       200,       100}
		};
		
		final boolean[] scale = new boolean[]{false, true};
		
		KMedoids km = null;
		for(boolean b : scale) {
			final Array2DRowRealMatrix mat = new Array2DRowRealMatrix(data);
			km = new KMedoids(mat, new KMedoidsPlanner(1).setScale(b));
			km.fit();
			assertTrue(km.didConverge());
		}
	}
	
	@Test
	public void KMedoidsLoadTest1() {
		final Array2DRowRealMatrix mat = getRandom(1000, 10);
		final boolean[] scale = new boolean[] {false, true};
		final int[] ks = new int[] {1,3,5};
		
		KMedoids km = null;
		for(boolean b : scale) {
			for(int k : ks) {
				km = new KMedoids(mat, 
						new KMedoidsPlanner(k)
							.setScale(b) );
				km.fit();
			}
		}
	}

	@Test
	public void KMedoidsLoadTest2FullLogger() {
		final Array2DRowRealMatrix mat = getRandom(1500, 10);
		KMedoids km = new KMedoids(mat, 
				new KMedoidsPlanner(5)
					.setScale(true)
					.setVerbose(true)
				);
		km.fit();
	}
	
	@Test
	public void KernelKMedoidsLoadTest1() {
		final Array2DRowRealMatrix mat = getRandom(1000, 10);
		final int[] ks = new int[] {1,3,5,7};
		Kernel kernel = new LaplacianKernel(0.05);
		
		KMedoids km = null;
		for(int k : ks) {
			km = new KMedoids(mat, 
					new KMedoids.KMedoidsPlanner(k)
						.setSep(kernel)
						.setVerbose(true)
						.setScale(false));
			km.fit();
		}
		System.out.println();
	}
	
	@Test
	public void KernelKMedoidsLoadTest2() {
		final Array2DRowRealMatrix mat = getRandom(2000, 10);
		final int[] ks = new int[] {12};
		Kernel kernel = new HyperbolicTangentKernel(); //SplineKernel();
		
		for(int k : ks) {
			new KMedoids(mat, 
				new KMedoids.KMedoidsPlanner(k)
					.setSep(kernel)
					.setVerbose(true)
					.setScale(false)).fit();
		}
		System.out.println();
	}

	@Test
	@Override
	public void testSerialization() throws IOException, ClassNotFoundException {
		KMedoids km = new KMedoids(data_,
			new KMedoids.KMedoidsPlanner(3)
				.setScale(true)
				.setVerbose(true)).fit();
		
		final double c = km.totalCost();
		km.saveModel(new FileOutputStream(TestSuite.tmpSerPath));
		assertTrue(TestSuite.file.exists());
		
		KMedoids km2 = (KMedoids)KMedoids.loadModel(new FileInputStream(TestSuite.tmpSerPath));
		assertTrue(km2.totalCost() == c);
		assertTrue(km2.equals(km));
		Files.delete(TestSuite.path);
	}
}