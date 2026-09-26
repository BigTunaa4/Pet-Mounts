package com.petmounts;

import java.awt.Color;
import net.runelite.api.JagexColor;

/**
 * Picks a small colour palette from a pet's model so the mount effects match the pet.
 */
final class PetPalette
{
	/** Used when a pet has no usable colours (e.g. fully textured). */
	static final Color[] DEFAULT = {new Color(0xA64DFF), new Color(0xFFD24D), new Color(0xE6CCFF)};

	private PetPalette()
	{
	}

	/**
	 * @param faceColors   the model's face colours (Jagex 16-bit HSL)
	 * @param faceTextures the model's face textures, or null; textured faces are skipped
	 * @return three colours: main, second, and a light accent
	 */
	static Color[] fromModel(short[] faceColors, short[] faceTextures)
	{
		if (faceColors == null || faceColors.length == 0)
		{
			return DEFAULT;
		}

		// Weigh each hue by how many faces use it, preferring colourful faces over grey ones.
		float[] weight = new float[JagexColor.HUE_MAX + 1];
		float[] satSum = new float[JagexColor.HUE_MAX + 1];
		float[] lumSum = new float[JagexColor.HUE_MAX + 1];
		float greyWeight = 0, greyLum = 0;

		for (int i = 0; i < faceColors.length; i++)
		{
			if (faceTextures != null && i < faceTextures.length && faceTextures[i] != -1)
			{
				continue;
			}
			short hsl = faceColors[i];
			int h = JagexColor.unpackHue(hsl);
			int s = JagexColor.unpackSaturation(hsl);
			int l = JagexColor.unpackLuminance(hsl);
			if (l < 12 || l > 120)
			{
				continue; // near black or white says little about the pet
			}
			if (s < 2)
			{
				greyWeight++;
				greyLum += l;
				continue;
			}
			float w = 1f + s / 3f;
			weight[h] += w;
			satSum[h] += s * w;
			lumSum[h] += l * w;
		}

		int first = maxIndex(weight, -1);
		if (first < 0)
		{
			if (greyWeight == 0)
			{
				return DEFAULT;
			}
			// A grey pet (e.g. rock golem): silvery smoke with a gold sparkle.
			Color grey = hslToColor(0, 0, Math.max(0.55f, greyLum / greyWeight / 127f));
			return new Color[]{grey, new Color(0xFFD24D), MountEffectsOverlay.lighten(grey, 0.5f)};
		}

		// Second colour: the strongest hue that isn't right next to the first one.
		float[] masked = weight.clone();
		for (int d = -4; d <= 4; d++)
		{
			masked[(first + d + 64) % 64] = 0;
		}
		int second = maxIndex(masked, -1);

		Color main = colourFor(first, weight, satSum, lumSum);
		Color other = second >= 0 && masked[second] > weight[first] * 0.15f
			? colourFor(second, weight, satSum, lumSum)
			: MountEffectsOverlay.lighten(main, 0.35f);
		return new Color[]{main, other, MountEffectsOverlay.lighten(main, 0.6f)};
	}

	private static Color colourFor(int hue, float[] weight, float[] satSum, float[] lumSum)
	{
		float s = satSum[hue] / weight[hue];
		float l = lumSum[hue] / weight[hue];
		// Brighten and saturate a little so effects read clearly against the game world.
		float hueF = hue / 64f + 1 / 128f;
		float satF = Math.min(1f, (s / 8f + 1 / 16f) * 1.25f);
		float lumF = Math.max(0.5f, Math.min(0.75f, l / 128f + 0.1f));
		return hslToColor(hueF, satF, lumF);
	}

	private static int maxIndex(float[] values, int fallback)
	{
		int best = fallback;
		float bestValue = 0;
		for (int i = 0; i < values.length; i++)
		{
			if (values[i] > bestValue)
			{
				bestValue = values[i];
				best = i;
			}
		}
		return best;
	}

	/** Standard HSL to RGB, all inputs 0..1. */
	static Color hslToColor(float h, float s, float l)
	{
		float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
		float p = 2 * l - q;
		return new Color(
			clamp(hueToRgb(p, q, h + 1f / 3f)),
			clamp(hueToRgb(p, q, h)),
			clamp(hueToRgb(p, q, h - 1f / 3f)));
	}

	private static float hueToRgb(float p, float q, float t)
	{
		if (t < 0)
		{
			t += 1;
		}
		if (t > 1)
		{
			t -= 1;
		}
		if (t < 1f / 6f)
		{
			return p + (q - p) * 6 * t;
		}
		if (t < 0.5f)
		{
			return q;
		}
		if (t < 2f / 3f)
		{
			return p + (q - p) * (2f / 3f - t) * 6;
		}
		return p;
	}

	private static float clamp(float v)
	{
		return Math.max(0f, Math.min(1f, v));
	}
}
