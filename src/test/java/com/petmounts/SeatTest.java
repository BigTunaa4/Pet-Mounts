package com.petmounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SeatTest
{
	/** A flat square "back" 60 units up (model y points down), made of two triangles, plus a lower "belly". */
	private static final float[] XS = {-20, 20, 20, -20, -20, 20, 20, -20};
	private static final float[] YS = {-60, -60, -60, -60, -20, -20, -20, -20};
	private static final float[] ZS = {-30, -30, 30, 30, -30, -30, 30, 30};
	private static final int[] F1 = {0, 0, 4, 4};
	private static final int[] F2 = {1, 2, 5, 6};
	private static final int[] F3 = {2, 3, 6, 7};

	@Test
	public void findsTheTopSurface()
	{
		SeatFinder.Seat s = SeatFinder.find(XS, YS, ZS, 8, F1, F2, F3, null, 4);
		assertNotNull(s);
		assertEquals(60f, s.height, 0.01f);
		assertEquals(1f, s.wa + s.wb + s.wc, 0.001f);
	}

	@Test
	public void ignoresSeeThroughEffects()
	{
		// Make the top square almost invisible: the seat drops to the solid surface below.
		byte[] alphas = {(byte) 200, (byte) 200, 0, 0};
		SeatFinder.Seat s = SeatFinder.find(XS, YS, ZS, 8, F1, F2, F3, alphas, 4);
		assertNotNull(s);
		assertEquals(20f, s.height, 0.01f);
	}

	@Test
	public void missesOutsideTheBody()
	{
		assertNull(SeatFinder.raycast(XS, YS, ZS, F1, F2, F3, null, 4, 100, 0));
	}

	@Test
	public void tunedSeatsCoverEveryRideablePet()
	{
		assertEquals(191 + 91, MountFits.size()); // pets plus creatures
		MountFits.Fit darkCore = MountFits.get(318);
		assertNotNull(darkCore);
		assertEquals(RiderPose.CROSS_LEGGED, darkCore.pose);
		assertEquals(1f, darkCore.wa + darkCore.wb + darkCore.wc, 0.001f);
	}
}
