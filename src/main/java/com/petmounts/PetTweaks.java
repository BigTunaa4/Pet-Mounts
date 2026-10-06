package com.petmounts;

import net.runelite.client.config.ConfigManager;

/**
 * Adjustments the player has saved for one pet (by name, so every colour of a pet shares them):
 * size, seat height, seat forward/back, riding pose, saddle style and saddle size. Set from the Mount Stable panel.
 */
final class PetTweaks
{
	static final PetTweaks NONE = new PetTweaks(100, 0, 0, RiderPose.AUTO, null, 0);
	/** Config keys holding saved adjustments start with this. */
	static final String KEY_PREFIX = "tweak_";

	final int size;
	final int seatHeight;
	final int seatForward;
	final RiderPose pose;
	/** This pet's own saddle style, or null to use the default style from the settings. */
	final SaddleStyle saddle;
	/** Saddle and blanket size in percent of the fitted size, or 0 for the mount's own fitted size. */
	final int saddleSize;

	PetTweaks(int size, int seatHeight, int seatForward, RiderPose pose, SaddleStyle saddle, int saddleSize)
	{
		this.size = Math.max(60, Math.min(160, size));
		this.seatHeight = Math.max(-40, Math.min(40, seatHeight));
		this.seatForward = Math.max(-60, Math.min(60, seatForward));
		this.pose = pose == null ? RiderPose.AUTO : pose;
		this.saddle = saddle;
		this.saddleSize = saddleSize <= 0 ? 0 : Math.max(SADDLE_MIN, Math.min(SADDLE_MAX, saddleSize));
	}

	static final int SADDLE_MIN = 60;
	static final int SADDLE_MAX = 140;

	/** The same adjustments with the saddle size filled in from the mount's fitted size when it isn't set. */
	PetTweaks withSaddleSizeOr(int fitted)
	{
		return saddleSize > 0 ? this : new PetTweaks(size, seatHeight, seatForward, pose, saddle, fitted);
	}

	/** The saddle style to use for this pet: its own, or the default when it has none. */
	SaddleStyle saddleOr(SaddleStyle fallback)
	{
		return saddle != null ? saddle : fallback;
	}

	boolean isDefault()
	{
		return size == 100 && seatHeight == 0 && seatForward == 0 && pose == RiderPose.AUTO && saddle == null
			&& saddleSize == 0;
	}

	static PetTweaks load(ConfigManager configManager, String petName)
	{
		String raw = configManager.getConfiguration(PetMountsConfig.GROUP, key(petName));
		if (raw == null || raw.isEmpty())
		{
			return NONE;
		}
		try
		{
			String[] p = raw.split(";");
			// The saddle style was added later, so older saved adjustments have only four parts.
			SaddleStyle saddle = p.length > 4 && !p[4].isEmpty() ? SaddleStyle.valueOf(p[4]) : null;
			int saddleSize = p.length > 5 ? Integer.parseInt(p[5]) : 0;
			return new PetTweaks(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
				RiderPose.valueOf(p[3]), saddle, saddleSize);
		}
		catch (RuntimeException e)
		{
			return NONE;
		}
	}

	static void save(ConfigManager configManager, String petName, PetTweaks t)
	{
		if (t.isDefault())
		{
			configManager.unsetConfiguration(PetMountsConfig.GROUP, key(petName));
		}
		else
		{
			configManager.setConfiguration(PetMountsConfig.GROUP, key(petName),
				t.size + ";" + t.seatHeight + ";" + t.seatForward + ";" + t.pose.name()
					+ ";" + (t.saddle != null ? t.saddle.name() : "") + ";" + t.saddleSize);
		}
	}

	private static String key(String petName)
	{
		return KEY_PREFIX + PetRules.normalize(petName).replaceAll("[^a-z0-9]+", "_");
	}
}
