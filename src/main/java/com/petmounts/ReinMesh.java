package com.petmounts;

/**
 * Reins from the rider's hands to the corners of the mount's mouth.
 *
 * Everything is in the mount's model space: x sideways, y pointing down, z toward the tail. Each rein is a thin
 * three-sided leather strap that sags a little between hand and mouth, rebuilt every frame so it follows both
 * the rider and the mount's head.
 */
final class ReinMesh
{
	/** Dark leather, like the saddle's straps. */
	static final short COLOR = SaddleMesh.hsl(6, 3, 18);

	static final int SEGMENTS = 6;
	private static final int SIDES = 3;
	private static final float RADIUS = 0.75f;
	/** How much the reins hang down, as a share of their length. */
	private static final float SAG = 0.18f;

	static final int VERTICES_PER_REIN = (SEGMENTS + 1) * SIDES;
	/** Both windings, so the strap is seen from every side. */
	static final int FACES_PER_REIN = SEGMENTS * SIDES * 2 * 2;

	/**
	 * Where each hand is while holding the reins, measured on the player model in each pose: sideways, height
	 * above the feet, and along the body (negative is forward). Left hand first. Null if the pose has no hands
	 * free for reins (cross-legged and standing riders hold on with their legs).
	 */
	static int[][] handsFor(RiderPose pose)
	{
		switch (pose)
		{
			case WIDE:
				return new int[][]{{-8, 141, -39}, {6, 124, -41}};
			case EXTRA_WIDE:
				return new int[][]{{-30, 56, 15}, {30, 56, 15}};
			case SADDLE:
				return new int[][]{{-14, 98, -24}, {14, 94, -21}};
			default:
				return null;
		}
	}

	final float[] x = new float[2 * VERTICES_PER_REIN];
	final float[] y = new float[2 * VERTICES_PER_REIN];
	final float[] z = new float[2 * VERTICES_PER_REIN];
	final int[] f1 = new int[2 * FACES_PER_REIN];
	final int[] f2 = new int[2 * FACES_PER_REIN];
	final int[] f3 = new int[2 * FACES_PER_REIN];

	ReinMesh()
	{
		// The faces never change; only the vertices move.
		int f = 0;
		for (int rein = 0; rein < 2; rein++)
		{
			int base = rein * VERTICES_PER_REIN;
			for (int s = 0; s < SEGMENTS; s++)
			{
				for (int k = 0; k < SIDES; k++)
				{
					int a = base + s * SIDES + k;
					int b = base + s * SIDES + (k + 1) % SIDES;
					int c = a + SIDES;
					int d = b + SIDES;
					f = face(f, a, b, d);
					f = face(f, a, d, c);
					f = face(f, a, d, b);
					f = face(f, a, c, d);
				}
			}
		}
	}

	private int face(int f, int a, int b, int c)
	{
		f1[f] = a;
		f2[f] = b;
		f3[f] = c;
		return f + 1;
	}

	int vertexCount()
	{
		return x.length;
	}

	int faceCount()
	{
		return f1.length;
	}

	/** Lays both reins: {@code hands} and {@code bits} are left then right, each {x, y, z}. */
	void update(float[][] hands, float[][] bits)
	{
		for (int rein = 0; rein < 2; rein++)
		{
			strap(rein * VERTICES_PER_REIN, hands[rein], bits[rein]);
		}
	}

	private void strap(int base, float[] a, float[] b)
	{
		float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-3f)
		{
			len = 1e-3f;
		}
		float sag = SAG * len;

		// Two directions across the strap: sideways (level), and the one square to both.
		float ux = -dz, uz = dx, uy = 0;
		float ul = (float) Math.sqrt(ux * ux + uz * uz);
		if (ul < 1e-3f)
		{
			ux = 1;
			uz = 0;
			ul = 1;
		}
		ux /= ul;
		uz /= ul;
		float tx = dx / len, ty = dy / len, tz = dz / len;
		float wx = ty * uz - tz * uy, wy = tz * ux - tx * uz, wz = tx * uy - ty * ux;

		for (int s = 0; s <= SEGMENTS; s++)
		{
			float t = s / (float) SEGMENTS;
			float px = a[0] + dx * t;
			float py = a[1] + dy * t + sag * 4 * t * (1 - t); // y points down, so this hangs
			float pz = a[2] + dz * t;
			for (int k = 0; k < SIDES; k++)
			{
				double ang = Math.PI / 2 + k * 2 * Math.PI / SIDES;
				float cu = (float) Math.cos(ang) * RADIUS, cw = (float) Math.sin(ang) * RADIUS;
				int v = base + s * SIDES + k;
				x[v] = px + cu * ux + cw * wx;
				y[v] = py + cu * uy + cw * wy;
				z[v] = pz + cu * uz + cw * wz;
			}
		}
	}

	/**
	 * Finds the corners of the mount's mouth, where the bit sits: the lowest part of the frontmost tip of the
	 * head, taking its left-most and right-most points. Returns {left, right} vertex indices, or null if the
	 * pet has no head in front of the seat (floating orbs, for instance).
	 */
	static int[] findBit(float[] xs, float[] ys, float[] zs, int vertexCount, int[] fa, int[] fb, int[] fc,
		byte[] alphas, int faceCount, float seatZ, float seatHeight)
	{
		boolean[] solid = new boolean[vertexCount];
		float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (int f = 0; f < faceCount; f++)
		{
			if (alphas != null && (alphas[f] & 0xFF) >= SeatFinder.SOLID_ALPHA)
			{
				continue;
			}
			for (int v : new int[]{fa[f], fb[f], fc[f]})
			{
				if (v >= 0 && v < vertexCount && !solid[v])
				{
					solid[v] = true;
					minZ = Math.min(minZ, zs[v]);
					maxZ = Math.max(maxZ, zs[v]);
				}
			}
		}
		if (minZ > maxZ)
		{
			return null;
		}
		float length = maxZ - minZ;

		// Solid points well in front of the seat and up at head height.
		int count = 0;
		float tip = Float.MAX_VALUE;
		for (int v = 0; v < vertexCount; v++)
		{
			if (solid[v] && zs[v] < seatZ - 0.12f * length && -ys[v] > 0.45f * seatHeight)
			{
				count++;
				tip = Math.min(tip, zs[v]);
			}
			else
			{
				solid[v] = false;
			}
		}
		if (count < 3)
		{
			return null;
		}

		// The tip of the head, then the lower half of it: the mouth.
		float reach = tip + Math.max(6, 0.08f * length);
		java.util.List<Integer> cluster = new java.util.ArrayList<>();
		for (int v = 0; v < vertexCount; v++)
		{
			if (solid[v] && zs[v] < reach)
			{
				cluster.add(v);
			}
		}
		float[] heights = new float[cluster.size()];
		for (int i = 0; i < heights.length; i++)
		{
			heights[i] = -ys[cluster.get(i)];
		}
		float[] sorted = heights.clone();
		java.util.Arrays.sort(sorted);
		float median = sorted.length % 2 == 1 ? sorted[sorted.length / 2]
			: (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2;
		int low = 0;
		for (float h : heights)
		{
			if (h <= median)
			{
				low++;
			}
		}

		int left = -1, right = -1;
		for (int i = 0; i < heights.length; i++)
		{
			if (low >= 2 && heights[i] > median)
			{
				continue;
			}
			int v = cluster.get(i);
			if (left < 0 || xs[v] < xs[left])
			{
				left = v;
			}
			if (right < 0 || xs[v] > xs[right])
			{
				right = v;
			}
		}
		if (left < 0 || left == right)
		{
			return null;
		}
		return new int[]{left, right};
	}
}
