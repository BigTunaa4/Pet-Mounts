package com.petmounts;

import net.runelite.api.Model;

/**
 * Makes the rider move with the mount's back.
 *
 * RuneLite objects can't be attached to another object's bones, so this picks the handful of
 * vertices on the mount closest to the seat, measures how far their average moves as the
 * mount animates, and hands that movement to the rider. Averaging several vertices avoids the
 * jitter of following a single one, and the movement is clamped so a stray neck or tail
 * vertex can never drag the rider off the mount.
 *
 * Adapted from the seat-anchor approach in Rapid Mounts by RapidUrsa (BSD 2-Clause);
 * see THIRD_PARTY_NOTICES.md.
 */
final class SeatAnchor
{
	private static final int VERTEX_COUNT = 12;

	private int[] vertices;
	private float[] base;
	private int expectedVertexCount;

	private int maxHeight;
	private int maxForward;
	private int maxSideways;

	/** Current offset of the seat from where it started, in local units. */
	int height;
	int forward;
	int sideways;

	/**
	 * Chooses the vertices nearest the seat.
	 *
	 * @param model      the mount model at rest
	 * @param seatHeight seat height above the model's origin (up is positive)
	 * @param seatForward seat position toward the head (forward is positive)
	 * @param mountHeight overall mount height, used to scale how far the seat may move
	 */
	void select(Model model, int seatHeight, int seatForward, int mountHeight)
	{
		reset();
		if (model == null || model.getVerticesCount() == 0)
		{
			return;
		}

		int count = model.getVerticesCount();
		int wanted = Math.min(VERTEX_COUNT, count);
		int[] nearest = new int[wanted];
		float[] distances = new float[wanted];
		java.util.Arrays.fill(nearest, -1);
		java.util.Arrays.fill(distances, Float.MAX_VALUE);

		// Model axes: x is sideways, y points down, z points toward the tail.
		float wantedY = -seatHeight;
		float wantedZ = -seatForward;
		float[] xs = model.getVerticesX();
		float[] ys = model.getVerticesY();
		float[] zs = model.getVerticesZ();
		for (int v = 0; v < count; v++)
		{
			float dx = xs[v];
			float dy = ys[v] - wantedY;
			float dz = zs[v] - wantedZ;
			float d = dx * dx + dy * dy + dz * dz;
			for (int slot = 0; slot < wanted; slot++)
			{
				if (d < distances[slot])
				{
					System.arraycopy(distances, slot, distances, slot + 1, wanted - slot - 1);
					System.arraycopy(nearest, slot, nearest, slot + 1, wanted - slot - 1);
					distances[slot] = d;
					nearest[slot] = v;
					break;
				}
			}
		}

		vertices = nearest;
		expectedVertexCount = count;
		// Bigger mounts may move the seat a little further.
		maxHeight = Math.max(14, mountHeight / 10);
		maxForward = Math.max(8, mountHeight / 16);
		maxSideways = Math.max(6, mountHeight / 24);
	}

	/** Measures the seat on the mount's current animation frame. */
	void update(Model animated)
	{
		if (vertices == null || animated == null || animated.getVerticesCount() != expectedVertexCount)
		{
			height = forward = sideways = 0;
			return;
		}

		float[] xs = animated.getVerticesX();
		float[] ys = animated.getVerticesY();
		float[] zs = animated.getVerticesZ();
		float x = 0, y = 0, z = 0;
		int n = 0;
		for (int v : vertices)
		{
			if (v >= 0)
			{
				x += xs[v];
				y += ys[v];
				z += zs[v];
				n++;
			}
		}
		if (n == 0)
		{
			return;
		}
		x /= n;
		y /= n;
		z /= n;

		if (base == null)
		{
			base = new float[]{x, y, z};
		}

		sideways = clamp(Math.round(x - base[0]), maxSideways);
		height = clamp(Math.round(-(y - base[1])), maxHeight);
		forward = clamp(Math.round(-(z - base[2])), maxForward);
	}

	void reset()
	{
		vertices = null;
		base = null;
		height = forward = sideways = 0;
	}

	private static int clamp(int value, int limit)
	{
		return Math.max(-limit, Math.min(limit, value));
	}
}
