package com.petmounts;

/**
 * Finds a seat on a pet the plugin has no tuned seat for (for example a pet released after this version).
 *
 * It drops a line straight down through the middle of the pet's body and takes the highest solid triangle it
 * hits: that point on the pet's back is where the rider sits. The same method was used, on the game's models,
 * to build {@link MountFits}.
 *
 * Coordinates are model space: x sideways, y pointing down, z toward the tail.
 */
final class SeatFinder
{
	/** Faces at least this transparent are effects (smoke, glow), not something to sit on. */
	static final int SOLID_ALPHA = 128;

	static final class Seat
	{
		final int a, b, c;
		final float wa, wb, wc;
		/** Height of the seat above the model's origin (up is positive). */
		final float height;

		Seat(int a, int b, int c, float wa, float wb, float wc, float height)
		{
			this.a = a;
			this.b = b;
			this.c = c;
			this.wa = wa;
			this.wb = wb;
			this.wc = wc;
			this.height = height;
		}
	}

	private SeatFinder()
	{
	}

	/**
	 * @param alphas face transparency (0 = opaque, 255 = invisible), or null if every face is opaque
	 * @return the seat under the middle of the body, or null if nothing solid was found
	 */
	static Seat find(float[] xs, float[] ys, float[] zs, int vertexCount,
		int[] f1, int[] f2, int[] f3, byte[] alphas, int faceCount)
	{
		// Seat front-to-back: the middle of the body's surface area. Thin necks and tails count for little.
		double areaSum = 0, zSum = 0;
		for (int f = 0; f < faceCount; f++)
		{
			if (!solid(alphas, f))
			{
				continue;
			}
			int a = f1[f], b = f2[f], c = f3[f];
			double ux = xs[b] - xs[a], uy = ys[b] - ys[a], uz = zs[b] - zs[a];
			double vx = xs[c] - xs[a], vy = ys[c] - ys[a], vz = zs[c] - zs[a];
			double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
			double area = Math.sqrt(cx * cx + cy * cy + cz * cz);
			areaSum += area;
			zSum += area * (zs[a] + zs[b] + zs[c]) / 3.0;
		}
		if (areaSum <= 0)
		{
			return null;
		}
		float seatZ = (float) (zSum / areaSum);

		// Seat side-to-side: the body's centre line, unless the model is built off-centre.
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
		for (int v = 0; v < vertexCount; v++)
		{
			if (Math.abs(zs[v] - seatZ) < 8)
			{
				minX = Math.min(minX, xs[v]);
				maxX = Math.max(maxX, xs[v]);
			}
		}
		float seatX = 0;
		if (minX <= maxX)
		{
			float mid = (minX + maxX) / 2, half = (maxX - minX) / 2;
			seatX = Math.abs(mid) < 0.3f * half ? 0 : mid;
		}

		// Step sideways if the line slips through a gap between triangles.
		for (float dx : new float[]{0, 2, -2, 5, -5, 9, -9})
		{
			Seat s = raycast(xs, ys, zs, f1, f2, f3, alphas, faceCount, seatX + dx, seatZ);
			if (s != null)
			{
				return s;
			}
		}
		return null;
	}

	/** The highest solid triangle directly under (x, z). */
	static Seat raycast(float[] xs, float[] ys, float[] zs, int[] f1, int[] f2, int[] f3,
		byte[] alphas, int faceCount, float x, float z)
	{
		Seat best = null;
		for (int f = 0; f < faceCount; f++)
		{
			if (!solid(alphas, f))
			{
				continue;
			}
			int a = f1[f], b = f2[f], c = f3[f];
			float ax = xs[a], az = zs[a], bx = xs[b], bz = zs[b], cx = xs[c], cz = zs[c];
			float d = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz);
			if (Math.abs(d) < 1e-6f)
			{
				continue; // seen edge-on from above
			}
			float wa = ((bz - cz) * (x - cx) + (cx - bx) * (z - cz)) / d;
			float wb = ((cz - az) * (x - cx) + (ax - cx) * (z - cz)) / d;
			float wc = 1 - wa - wb;
			if (wa < -1e-4f || wb < -1e-4f || wc < -1e-4f)
			{
				continue;
			}
			float height = -(wa * ys[a] + wb * ys[b] + wc * ys[c]);
			if (best == null || height > best.height)
			{
				best = new Seat(a, b, c, wa, wb, wc, height);
			}
		}
		return best;
	}

	private static boolean solid(byte[] alphas, int face)
	{
		return alphas == null || (alphas[face] & 0xFF) < SOLID_ALPHA;
	}
}
