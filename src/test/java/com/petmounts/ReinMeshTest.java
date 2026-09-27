package com.petmounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ReinMeshTest
{
	@Test
	public void fitsInTheSaddleTemplate()
	{
		ReinMesh m = new ReinMesh();
		assertTrue(m.vertexCount() <= 394 && m.faceCount() <= 788);
		for (int f = 0; f < m.faceCount(); f++)
		{
			assertTrue(m.f1[f] < m.vertexCount() && m.f2[f] < m.vertexCount() && m.f3[f] < m.vertexCount());
		}
	}

	@Test
	public void runsFromHandToMouthAndHangs()
	{
		ReinMesh m = new ReinMesh();
		float[] hand = {-8, -140, -30};
		float[] mouth = {-6, -90, -110};
		m.update(new float[][]{hand, {8, -140, -30}}, new float[][]{mouth, {6, -90, -110}});
		float[] start = ring(m, 0), end = ring(m, ReinMesh.SEGMENTS), mid = ring(m, ReinMesh.SEGMENTS / 2);
		for (int k = 0; k < 3; k++)
		{
			assertEquals(hand[k], start[k], 0.01);
			assertEquals(mouth[k], end[k], 0.01);
		}
		float straightY = (hand[1] + mouth[1]) / 2;
		assertTrue("the middle hangs below a straight line (y points down)", mid[1] > straightY + 5);
		for (int v = 0; v < m.vertexCount(); v++)
		{
			assertTrue(!Float.isNaN(m.x[v]) && !Float.isNaN(m.y[v]) && !Float.isNaN(m.z[v]));
		}
	}

	@Test
	public void noHandsFreeWhenSittingOnTop()
	{
		assertNull(ReinMesh.handsFor(RiderPose.CROSS_LEGGED));
		assertNull(ReinMesh.handsFor(RiderPose.STANDING));
		assertNotNull(ReinMesh.handsFor(RiderPose.WIDE));
		assertNotNull(ReinMesh.handsFor(RiderPose.EXTRA_WIDE));
		assertNotNull(ReinMesh.handsFor(RiderPose.SADDLE));
	}

	@Test
	public void findsTheCornersOfTheMouth()
	{
		// A blocky horse: body from z -60 to 60 at 60-100 high, a head in front at z -100 to -70, 90-130 high.
		Shape s = new Shape();
		s.box(-20, 20, 60, 100, -60, 60);
		s.box(-10, 10, 90, 130, -100, -70);
		int[] bit = s.findBit(0, 100);
		assertNotNull(bit);
		assertTrue("left then right", s.x(bit[0]) < 0 && s.x(bit[1]) > 0);
		assertEquals(-100, s.z(bit[0]), 0.01);
		assertEquals(90, -s.y(bit[0]), 0.01);
	}

	@Test
	public void noMouthOnARoundOrb()
	{
		Shape s = new Shape();
		s.box(-30, 30, 10, 70, -30, 30); // nothing in front of the seat at head height
		assertNull(s.findBit(0, 70));
	}

	private static float[] ring(ReinMesh m, int s)
	{
		float[] c = new float[3];
		for (int k = 0; k < 3; k++)
		{
			int v = s * 3 + k;
			c[0] += m.x[v] / 3;
			c[1] += m.y[v] / 3;
			c[2] += m.z[v] / 3;
		}
		return c;
	}

	/** Boxes as triangles, in model space (y points down). */
	private static final class Shape
	{
		final List<float[]> v = new ArrayList<>();
		final List<int[]> f = new ArrayList<>();

		void box(float x0, float x1, float h0, float h1, float z0, float z1)
		{
			int o = v.size();
			for (int i = 0; i < 8; i++)
			{
				v.add(new float[]{(i & 1) == 0 ? x0 : x1, -((i & 2) == 0 ? h0 : h1), (i & 4) == 0 ? z0 : z1});
			}
			int[][] quads = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
			for (int[] q : quads)
			{
				f.add(new int[]{o + q[0], o + q[1], o + q[2]});
				f.add(new int[]{o + q[0], o + q[2], o + q[3]});
			}
		}

		float x(int i)
		{
			return v.get(i)[0];
		}

		float y(int i)
		{
			return v.get(i)[1];
		}

		float z(int i)
		{
			return v.get(i)[2];
		}

		int[] findBit(float seatZ, float seatHeight)
		{
			int n = v.size(), m = f.size();
			float[] xs = new float[n], ys = new float[n], zs = new float[n];
			for (int i = 0; i < n; i++)
			{
				xs[i] = x(i);
				ys[i] = y(i);
				zs[i] = z(i);
			}
			int[] a = new int[m], b = new int[m], c = new int[m];
			for (int i = 0; i < m; i++)
			{
				a[i] = f.get(i)[0];
				b[i] = f.get(i)[1];
				c[i] = f.get(i)[2];
			}
			return ReinMesh.findBit(xs, ys, zs, n, a, b, c, null, m, seatZ, seatHeight);
		}
	}
}
