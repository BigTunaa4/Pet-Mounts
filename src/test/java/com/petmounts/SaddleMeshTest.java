package com.petmounts;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SaddleMeshTest
{
	/** A rounded back: level on top, falling away down the sides. */
	private static final SaddleMesh.Surface BACK = (dx, dz) -> Math.abs(dx) < 30 ? -dx * dx / 60f : Float.NaN;

	@Test
	public void fitsInTheTemplateAndSitsOnTheBack()
	{
		SaddleMesh m = SaddleMesh.build(BACK, 105, false, true, SaddleMesh.hsl(40, 5, 40), SaddleMesh.GOLD, 788, 1576);
		assertTrue(m.vertexCount > 100 && m.vertexCount <= 788);
		assertTrue(m.faceCount > 100 && m.faceCount <= 1576);
		assertTrue("rider sits on top of the saddle", m.seatThickness > 3 && m.seatThickness < 12);
		for (int f = 0; f < m.faceCount; f++)
		{
			assertTrue(m.f1[f] < m.vertexCount && m.f2[f] < m.vertexCount && m.f3[f] < m.vertexCount);
		}
		for (int v = 0; v < m.vertexCount; v++)
		{
			assertTrue(!Float.isNaN(m.x[v]) && !Float.isNaN(m.y[v]) && !Float.isNaN(m.z[v]));
		}
	}

	@Test
	public void blanketOnlyForSittingCrossLegged()
	{
		SaddleMesh withSaddle = SaddleMesh.build(BACK, 105, false, true, (short) 0, (short) 0, 788, 1576);
		SaddleMesh rug = SaddleMesh.build(BACK, 105, false, false, (short) 0, (short) 0, 788, 1576);
		assertTrue(rug.faceCount < withSaddle.faceCount);
	}

	@Test
	public void neverOverflowsASmallTemplate()
	{
		SaddleMesh m = SaddleMesh.build(BACK, 105, true, true, (short) 0, (short) 0, 120, 200);
		assertTrue(m.vertexCount <= 120 && m.faceCount <= 200);
	}
}
