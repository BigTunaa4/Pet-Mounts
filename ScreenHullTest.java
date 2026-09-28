package com.petmounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.Point;
import java.awt.Polygon;
import java.util.Arrays;
import org.junit.Test;

public class ScreenHullTest
{
	@Test
	public void outlinesTheOuterPoints()
	{
		Polygon p = ScreenHull.of(Arrays.asList(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10),
			new Point(5, 5), new Point(3, 7)));
		assertEquals(4, p.npoints);
		assertTrue(p.contains(5, 5));
		assertFalse(p.contains(11, 5));
	}
}
