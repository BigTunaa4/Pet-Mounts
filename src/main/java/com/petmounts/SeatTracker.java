package com.petmounts;

import net.runelite.api.Model;

/**
 * Follows one spot on the mount's back as the mount animates.
 *
 * RuneLite objects can't be attached to another object's bones, so the seat is stored as a triangle on the
 * mount and a position inside it. Each frame the triangle's three vertices are read from the animated model and
 * the seat is rebuilt from them, so the rider rises, dips and sways with the pet's back.
 *
 * The idea of tracking the mount's vertices to move a separately drawn rider comes from Rapid Mounts by
 * RapidUrsa (BSD 2-Clause); see THIRD_PARTY_NOTICES.md.
 */
final class SeatTracker
{
	private int a = -1, b, c;
	private float wa, wb, wc;
	private int vertexCount;

	/** Where the seat is at rest, and how far it may move from there (guards against a bad frame). */
	private float restX, restY, restZ;
	private float maxMove;

	/** Current seat in model space: x sideways, y pointing down, z toward the tail. */
	float x, y, z;

	void set(int a, int b, int c, float wa, float wb, float wc, Model rest, float mountHeight)
	{
		this.a = a;
		this.b = b;
		this.c = c;
		this.wa = wa;
		this.wb = wb;
		this.wc = wc;
		this.vertexCount = rest.getVerticesCount();
		this.maxMove = Math.max(20, mountHeight / 2);
		read(rest);
		restX = x;
		restY = y;
		restZ = z;
	}

	boolean isSet()
	{
		return a >= 0;
	}

	void clear()
	{
		a = -1;
	}

	/** Reads the seat from the mount's current animation frame. */
	void update(Model animated)
	{
		if (a < 0 || animated == null || animated.getVerticesCount() != vertexCount)
		{
			return;
		}
		float px = x, py = y, pz = z;
		read(animated);
		if (Math.abs(x - restX) > maxMove || Math.abs(y - restY) > maxMove || Math.abs(z - restZ) > maxMove)
		{
			// A frame moved the seat somewhere implausible: keep the last good position.
			x = px;
			y = py;
			z = pz;
		}
	}

	private void read(Model m)
	{
		float[] xs = m.getVerticesX();
		float[] ys = m.getVerticesY();
		float[] zs = m.getVerticesZ();
		x = wa * xs[a] + wb * xs[b] + wc * xs[c];
		y = wa * ys[a] + wb * ys[b] + wc * ys[c];
		z = wa * zs[a] + wb * zs[b] + wc * zs[c];
	}
}
