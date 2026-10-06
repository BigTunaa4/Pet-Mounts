package com.petmounts;

/**
 * Builds the saddle and blanket geometry for one mount, moulded to that pet's back.
 *
 * The game can't create new models from scratch, so the plugin loads a spare model and reshapes it into this
 * geometry (the same approach Rapid Mounts uses for its custom saddle). This class only produces the shape:
 * vertex positions, triangles and colours in model space (x sideways, y pointing down, z toward the tail),
 * centred on the seat point on the pet's back.
 *
 * The blanket and seat follow the pet's real back: each point is dropped onto the pet's model in its idle
 * pose, and where the body falls away the cloth hangs down the side instead of floating or sinking in.
 *
 * Every triangle is emitted in both windings so the thin leather and cloth look solid from either side.
 */
final class SaddleMesh
{
	/** Height of the pet's back at a point relative to the seat (up positive), or NaN where there's nothing. */
	interface Surface
	{
		float heightAt(float dx, float dz);
	}

	// Colours in the game's 16-bit HSL: hue (0-63), saturation (0-7), lightness (0-127).
	static final short LEATHER = hsl(6, 4, 38);
	static final short LEATHER_DARK = hsl(6, 3, 20);
	static final short STEEL = hsl(0, 0, 72);
	static final short GOLD = hsl(8, 5, 62);
	static final short SILVER = hsl(0, 0, 96);
	/** The biggest the blanket gets (half its width, and its length), however broad the pet's back. */
	static final float MAX_BLANKET_HALF_WIDTH = 44;
	static final float MAX_BLANKET_LENGTH = 66;

	private static final float BLANKET_GAP = 0.8f;
	private static final float BLANKET_THICKNESS = 1.2f;
	private static final float SEAT_THICKNESS = 3.0f;

	final float[] x;
	final float[] y;
	final float[] z;
	final int[] f1;
	final int[] f2;
	final int[] f3;
	final short[] color;
	int vertexCount;
	int faceCount;

	/** How far the top of the seat sits above the pet's back, so the rider sits on the saddle. */
	float seatThickness;

	private SaddleMesh(int maxVertices, int maxFaces)
	{
		x = new float[maxVertices];
		y = new float[maxVertices];
		z = new float[maxVertices];
		f1 = new int[maxFaces];
		f2 = new int[maxFaces];
		f3 = new int[maxFaces];
		color = new short[maxFaces];
	}

	/**
	 * @param surface    the pet's back around the seat
	 * @param seatHeight the seat's height above the ground
	 * @param wideSeat   the rider sits with knees spread wide, so the seat is a little wider
	 * @param saddle     include the leather saddle and stirrups (false = just the blanket, for sitting cross-legged)
	 * @param blanket    blanket colour
	 * @param trim       blanket edge colour
	 * @param style      leather and metal colours of the saddle
	 * @param scale      the size fitted to this mount (1 = as measured from its back)
	 */
	static SaddleMesh build(Surface surface, float seatHeight, boolean wideSeat, boolean saddle,
		short blanket, short trim, SaddleStyle style, float scale, int maxVertices, int maxFaces)
	{
		SaddleMesh m = new SaddleMesh(maxVertices, maxFaces);
		float back = backHalfWidth(surface, seatHeight);
		float length = backLength(surface, seatHeight);

		// The saddle is sized for the rider; the blanket is sized for the pet. Both fit this pet's back, both
		// across and front to back, so short-backed pets get a shorter saddle.
		float sw = Math.max(12, Math.min(wideSeat ? 24 : 19, back * 0.85f));
		float sl = Math.max(26, Math.min(38, length * 0.8f));
		// The blanket covers the back but stays in proportion to the rider, even on very broad, flat pets.
		float bw = Math.max(sw + 5, Math.min(Math.min(sw + 12, MAX_BLANKET_HALF_WIDTH), back * 1.1f));
		float bl = Math.max(sl + 8, Math.min(Math.min(MAX_BLANKET_LENGTH, bw * 1.6f), length * 1.1f));
		scale = Math.max(0.5f, Math.min(1.5f, scale));
		sw *= scale;
		sl *= scale;
		bw *= scale;
		bl *= scale;

		// Blanket: cloth over the back, hanging down both sides, with a trimmed edge.
		float[][] blanketGround = drape(surface, 9, 6, bw, bl);
		m.sheet(blanketGround, 9, 6, bw, bl, BLANKET_GAP, BLANKET_THICKNESS, 0, 0, blanket, trim);
		float onBlanket = BLANKET_GAP + BLANKET_THICKNESS + 0.3f;

		if (!saddle)
		{
			m.seatThickness = onBlanket - 0.3f;
			return m;
		}

		// Seat: leather on the blanket, with a raised cantle at the back and pommel at the front.
		float[][] seatGround = drape(surface, 7, 7, sw, sl);
		m.sheet(seatGround, 7, 7, sw, sl, onBlanket, SEAT_THICKNESS, 9, 6, style.leather, style.leatherDark);
		m.seatThickness = seatGround[3][3] + onBlanket + SEAT_THICKNESS;

		// Stirrup straps and stirrups hanging from each side of the seat.
		float strap = 14;
		for (int side = -1; side <= 1; side += 2)
		{
			float sx = side * sw * 0.97f;
			float edge = seatGround[3][side < 0 ? 0 : 6] + onBlanket; // height of the seat's side edge
			float top = -edge + 1;
			m.box(sx - 1.2f, sx + 1.2f, top, top + strap, -2.5f, 2.5f, style.leatherDark);
			float sy = top + strap;
			m.box(sx - 3.5f, sx + 3.5f, sy, sy + 1.5f, -4.5f, 4.5f, style.metal);
			m.box(sx - 3.5f, sx - 2.5f, sy - 5, sy, -4.5f, 4.5f, style.metal);
			m.box(sx + 2.5f, sx + 3.5f, sy - 5, sy, -4.5f, 4.5f, style.metal);
		}

		// A small horn on the pommel.
		float front = -sl / 2 * 0.9f;
		float pommelTop = seatGround[0][3] + onBlanket + SEAT_THICKNESS + 6;
		m.box(-1.8f, 1.8f, -(pommelTop + 4), -(pommelTop - 1), front - 1.8f, front + 1.8f, trim);
		return m;
	}

	/**
	 * Gold or silver fittings for this pet. Gold suits warm-coloured pets (browns, oranges, reds, whites);
	 * silver suits grey, black and cool-coloured ones (blues, greens, purples), where gold clashes.
	 *
	 * @param faceColors the pet's face colours (Jagex HSL)
	 * @param alphas     face transparency, or null
	 */
	static short trimFor(short[] faceColors, byte[] alphas)
	{
		if (faceColors == null || faceColors.length == 0)
		{
			return GOLD;
		}
		int[] hues = new int[64];
		int[] lums = new int[128];
		int faces = 0, grey = 0, colourful = 0;
		for (int f = 0; f < faceColors.length; f++)
		{
			if (alphas != null && f < alphas.length && (alphas[f] & 0xFF) >= 254)
			{
				continue;
			}
			int c = faceColors[f] & 0xFFFF;
			int hue = (c >> 10) & 63, sat = (c >> 7) & 7, lum = c & 127;
			faces++;
			lums[lum]++;
			if (sat < 2)
			{
				grey++;
			}
			else
			{
				colourful++;
				hues[hue]++;
			}
		}
		if (faces == 0)
		{
			return GOLD;
		}
		int median = 0;
		int seen = lums[0];
		while (median < 127 && seen < (faces + 1) / 2)
		{
			median++;
			seen += lums[median];
		}
		if (grey > faces * 0.6f)
		{
			return median < 75 ? SILVER : GOLD; // grey and black pets; white ones keep gold
		}
		int hue = 0;
		for (int h = 1; h < 64; h++)
		{
			if (hues[h] > hues[hue])
			{
				hue = h;
			}
		}
		if (colourful > 0 && hue >= 17 && hue <= 56)
		{
			return SILVER; // greens, blues and purples
		}
		return median < 16 ? SILVER : GOLD; // near-black pets
	}

	/**
	 * Half the width of the pet's back at the seat: how far out from the middle the back stays roughly level
	 * before it falls away down the pet's side.
	 */
	static float backHalfWidth(Surface surface, float seatHeight)
	{
		float fall = Math.max(6, seatHeight * 0.25f);
		float widest = 0;
		for (int dir = -1; dir <= 1; dir += 2)
		{
			float reach = 0;
			for (float dx = 2; dx <= 80; dx += 2)
			{
				float h = surface.heightAt(dir * dx, 0);
				if (Float.isNaN(h) || h < -fall)
				{
					break;
				}
				reach = dx;
			}
			widest = Math.max(widest, reach);
		}
		return Math.max(10, widest);
	}

	/**
	 * Length of the pet's back around the seat: twice the shorter of how far it stays roughly level toward the
	 * head and toward the tail, before the neck rises or the rump or shoulders fall away.
	 */
	static float backLength(Surface surface, float seatHeight)
	{
		float fall = Math.max(6, seatHeight * 0.25f);
		float rise = Math.max(5, seatHeight * 0.2f);
		float shortest = Float.MAX_VALUE;
		for (int dir = -1; dir <= 1; dir += 2)
		{
			float reach = 0;
			for (float dz = 2; dz <= 60; dz += 2)
			{
				float h = surface.heightAt(0, dir * dz);
				if (Float.isNaN(h) || h < -fall || h > rise)
				{
					break;
				}
				reach = dz;
			}
			shortest = Math.min(shortest, reach);
		}
		return Math.max(20, 2 * shortest);
	}

	/**
	 * Heights of the back on a grid (rows front to back, columns left to right), relative to the seat.
	 * Working out from the middle of each row: where the body drops away or there's nothing below, the cloth
	 * hangs down instead of following the surface to the ground or up onto a raised part.
	 */
	private static float[][] drape(Surface surface, int nx, int nz, float halfW, float length)
	{
		float[][] g = new float[nz][nx];
		int mid = nx / 2;
		float dx = 2 * halfW / (nx - 1);
		float prevCentre = 0;
		for (int j = 0; j < nz; j++)
		{
			float pz = (j / (float) (nz - 1) * 2 - 1) * length / 2;
			float c = surface.heightAt(0, pz);
			if (Float.isNaN(c) || Math.abs(c - prevCentre) > length * 0.5f)
			{
				c = prevCentre;
			}
			g[j][mid] = c;
			prevCentre = c;
			for (int dir = -1; dir <= 1; dir += 2)
			{
				float prev = c;
				for (int k = 1; k <= mid; k++)
				{
					int i = mid + dir * k;
					float px = (i / (float) (nx - 1) * 2 - 1) * halfW;
					float h = surface.heightAt(px, pz);
					float hang = prev - dx * (0.4f + 0.25f * k); // drapes more steeply further out
					if (Float.isNaN(h) || h > prev + dx * 0.4f || h < prev - dx * 1.4f)
					{
						h = hang;
					}
					g[j][i] = h;
					prev = h;
				}
			}
		}
		return g;
	}

	/** A shell over a height grid: underside `lift` above the grid, `thickness` thick, with raised ends. */
	private void sheet(float[][] ground, int nx, int nz, float halfW, float length, float lift, float thickness,
		float cantle, float pommel, short main, short edge)
	{
		int top = vertexCount;
		for (int layer = 0; layer < 2; layer++)
		{
			float up = lift + (layer == 0 ? thickness : 0);
			for (int j = 0; j < nz; j++)
			{
				float t = j / (float) (nz - 1) * 2 - 1; // -1 front .. 1 back
				for (int i = 0; i < nx; i++)
				{
					float s = i / (float) (nx - 1) * 2 - 1; // -1 left .. 1 right
					float middle = 1 - s * s;
					float raise = t > 0 ? cantle * t * t * t * middle : pommel * -t * -t * -t * middle;
					vertex(s * halfW, -(ground[j][i] + up + raise), t * length / 2);
				}
			}
		}
		int bottom = top + nx * nz;
		for (int j = 0; j < nz - 1; j++)
		{
			for (int i = 0; i < nx - 1; i++)
			{
				boolean rim = i == 0 || j == 0 || i == nx - 2 || j == nz - 2;
				short c = rim ? edge : main;
				int a = j * nx + i, b = a + 1, d = a + nx, e = d + 1;
				quad(top + a, top + b, top + e, top + d, c);
				quad(bottom + a, bottom + b, bottom + e, bottom + d, c);
			}
		}
		// Close the edges so the shell has visible thickness.
		for (int i = 0; i < nx - 1; i++)
		{
			quad(top + i, top + i + 1, bottom + i + 1, bottom + i, edge);
			int back = (nz - 1) * nx;
			quad(top + back + i, top + back + i + 1, bottom + back + i + 1, bottom + back + i, edge);
		}
		for (int j = 0; j < nz - 1; j++)
		{
			int l = j * nx, l2 = l + nx, r = l + nx - 1, r2 = r + nx;
			quad(top + l, top + l2, bottom + l2, bottom + l, edge);
			quad(top + r, top + r2, bottom + r2, bottom + r, edge);
		}
	}

	/** An axis-aligned box. y values are model y (down positive). */
	private void box(float x0, float x1, float y0, float y1, float z0, float z1, short c)
	{
		int v = vertexCount;
		vertex(x0, y0, z0);
		vertex(x1, y0, z0);
		vertex(x1, y1, z0);
		vertex(x0, y1, z0);
		vertex(x0, y0, z1);
		vertex(x1, y0, z1);
		vertex(x1, y1, z1);
		vertex(x0, y1, z1);
		quad(v, v + 1, v + 2, v + 3, c);
		quad(v + 4, v + 5, v + 6, v + 7, c);
		quad(v, v + 1, v + 5, v + 4, c);
		quad(v + 3, v + 2, v + 6, v + 7, c);
		quad(v, v + 3, v + 7, v + 4, c);
		quad(v + 1, v + 2, v + 6, v + 5, c);
	}

	private void vertex(float px, float py, float pz)
	{
		if (vertexCount >= x.length)
		{
			return;
		}
		x[vertexCount] = px;
		y[vertexCount] = py;
		z[vertexCount] = pz;
		vertexCount++;
	}

	private void quad(int a, int b, int c, int d, short col)
	{
		tri(a, b, c, col);
		tri(a, c, d, col);
	}

	private void tri(int a, int b, int c, short col)
	{
		if (faceCount + 2 > f1.length || a >= vertexCount || b >= vertexCount || c >= vertexCount)
		{
			return;
		}
		// Both windings, so the face is visible from either side.
		f1[faceCount] = a;
		f2[faceCount] = b;
		f3[faceCount] = c;
		color[faceCount++] = col;
		f1[faceCount] = a;
		f2[faceCount] = c;
		f3[faceCount] = b;
		color[faceCount++] = col;
	}

	static short hsl(int hue, int sat, int lum)
	{
		return (short) ((hue & 63) << 10 | (sat & 7) << 7 | (lum & 127));
	}
}
