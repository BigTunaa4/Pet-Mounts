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
		SaddleMesh m = SaddleMesh.build(BACK, 105, false, true, SaddleMesh.hsl(40, 5, 40), SaddleMesh.GOLD, SaddleStyle.CLASSIC, 788, 1576);
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
		SaddleMesh withSaddle = SaddleMesh.build(BACK, 105, false, true, (short) 0, (short) 0, SaddleStyle.CLASSIC, 788, 1576);
		SaddleMesh rug = SaddleMesh.build(BACK, 105, false, false, (short) 0, (short) 0, SaddleStyle.CLASSIC, 788, 1576);
		assertTrue(rug.faceCount < withSaddle.faceCount);
	}

	@Test
	public void neverOverflowsASmallTemplate()
	{
		SaddleMesh m = SaddleMesh.build(BACK, 105, true, true, (short) 0, (short) 0, SaddleStyle.CLASSIC, 120, 200);
		assertTrue(m.vertexCount <= 120 && m.faceCount <= 200);
	}

	@Test
	public void goldForWarmPetsSilverForDarkAndCoolOnes()
	{
		short brown = SaddleMesh.hsl(5, 4, 30);
		short black = SaddleMesh.hsl(0, 0, 10);
		short blue = SaddleMesh.hsl(38, 5, 60);
		short white = SaddleMesh.hsl(0, 0, 120);
		org.junit.Assert.assertEquals(SaddleMesh.GOLD, SaddleMesh.trimFor(new short[]{brown, brown, brown}, null));
		org.junit.Assert.assertEquals(SaddleMesh.SILVER, SaddleMesh.trimFor(new short[]{black, black, brown}, null));
		org.junit.Assert.assertEquals(SaddleMesh.SILVER, SaddleMesh.trimFor(new short[]{blue, blue, brown}, null));
		org.junit.Assert.assertEquals(SaddleMesh.GOLD, SaddleMesh.trimFor(new short[]{white, white, white}, null));
	}

	@Test
	public void blanketStaysInProportionOnBroadBacks()
	{
		SaddleMesh.Surface flat = (dx, dz) -> 0f; // a very broad, flat back
		SaddleMesh m = SaddleMesh.build(flat, 90, true, true, (short) 0, SaddleMesh.GOLD, SaddleStyle.CLASSIC, 788, 1576);
		float widest = 0, longest = 0;
		for (int v = 0; v < m.vertexCount; v++)
		{
			widest = Math.max(widest, Math.abs(m.x[v]));
			longest = Math.max(longest, Math.abs(m.z[v]));
		}
		assertTrue("blanket not wider than it should be", widest <= SaddleMesh.MAX_BLANKET_HALF_WIDTH + 6);
		assertTrue("blanket not longer than it should be", longest <= SaddleMesh.MAX_BLANKET_LENGTH / 2 + 6);
	}

	@Test
	public void saddleStylesChangeTheLeatherAndMetal()
	{
		SaddleMesh classic = SaddleMesh.build(BACK, 105, false, true, (short) 0, (short) 0, SaddleStyle.CLASSIC, 788, 1576);
		SaddleMesh skull = SaddleMesh.build(BACK, 105, false, true, (short) 0, (short) 0, SaddleStyle.SKULL, 788, 1576);
		java.util.Set<Short> a = new java.util.HashSet<>(), b = new java.util.HashSet<>();
		for (int i = 0; i < classic.faceCount; i++)
		{
			a.add(classic.color[i]);
		}
		for (int i = 0; i < skull.faceCount; i++)
		{
			b.add(skull.color[i]);
		}
		org.junit.Assert.assertTrue(a.contains(SaddleMesh.LEATHER));
		org.junit.Assert.assertFalse(b.contains(SaddleMesh.LEATHER));
		org.junit.Assert.assertTrue(b.contains(SaddleStyle.SKULL.metal));
		org.junit.Assert.assertEquals(classic.faceCount, skull.faceCount);
	}
}
