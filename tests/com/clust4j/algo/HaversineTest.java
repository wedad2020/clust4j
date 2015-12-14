package com.clust4j.algo;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.junit.Test;

import com.clust4j.algo.AbstractKCentroidClusterer.BaseKCentroidPlanner;
import com.clust4j.algo.KMedoids.KMedoidsPlanner;
import com.clust4j.utils.HaversineDistance;
import com.clust4j.utils.VecUtils;

public class HaversineTest {

	@Test
	public void test() {
		final double[][] coordinates = new double[][] {
			new double[]{30.2500, 97.7500}, // Austin, TX
			new double[]{32.7767, 96.7970}, // Dallas, TX
			new double[]{29.7604, 95.3698}, // Houston, TX
			new double[]{40.7903, 73.9597}, // Manhattan
			new double[]{40.7484, 73.9857}  // Empire State Bldg
		};
		
		final Array2DRowRealMatrix mat = new Array2DRowRealMatrix(coordinates, false);
		
		AbstractKCentroidClusterer km = new KMeans(mat, 
						new BaseKCentroidPlanner(2)
								.setVerbose(true)
								.setSep(new HaversineDistance())
								.setVerbose(true)
								.setScale(true)).fit();
		
		int[] kmlabels = km.getLabels();
		assertTrue(kmlabels[0] == kmlabels[1] && kmlabels[1] == kmlabels[2]);
		assertTrue(kmlabels[1] != kmlabels[3] && kmlabels[3] == kmlabels[4]);
		
		km = new KMedoids(mat, new KMedoidsPlanner(2)
								.setVerbose(true)
								.setSep(new HaversineDistance())
								.setVerbose(true)
								.setScale(false)).fit();
		assertTrue(kmlabels[0] == kmlabels[1] && kmlabels[1] == kmlabels[2]);
		assertTrue(kmlabels[1] != kmlabels[3] && kmlabels[3] == kmlabels[4]);
		assertTrue(kmlabels[0] == 0 && kmlabels[1] == 0 && kmlabels[2] == 0);
		assertTrue( VecUtils.equalsExactly(km.getCentroids().get(0), coordinates[0]) ); // First one should be Austin
		
		System.out.println( Arrays.toString(km.getCentroids().get(0)) + "; " + Arrays.toString(km.getCentroids().get(1)) );
	}

}