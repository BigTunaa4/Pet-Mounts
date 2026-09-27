package com.petmounts;

import net.runelite.client.config.ConfigManager;

/**
 * Adjustments the player has saved for one pet (by name, so every colour of a pet shares them):
 * size, seat height, seat forward/back and riding pose. Set from the Mount Stable panel.
 */
final class PetTweaks
{
	static final PetTweaks NONE = new PetTweaks(100, 0, 0, RiderPose.AUTO);
	/** Config keys holding saved adjustments start with this. */
	static final String KEY_PREFIX = "tweak_";

	final int size;
	final int seatHeight;
	final int seatForward;
	final RiderPose pose;

	PetTweaks(int size, int seatHeight, int seatForward, RiderPose pose)
	{
		this.size = Math.max(60, Math.min(160, size));
		this.seatHeight = Math.max(-40, Math.min(40, seatHeight));
		this.seatForward = Math.max(-60, Math.min(60, seatForward));
		this.pose = pose == null ? RiderPose.AUTO : pose;
	}

	boolean isDefault()
	{
		return size == 100 && seatHeight == 0 && seatForward == 0 && pose == RiderPose.AUTO;
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
			return new PetTweaks(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
				RiderPose.valueOf(p[3]));
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
				t.size + ";" + t.seatHeight + ";" + t.seatForward + ";" + t.pose.name());
		}
	}

	private static String key(String petName)
	{
		return KEY_PREFIX + PetRules.normalize(petName).replaceAll("[^a-z0-9]+", "_");
	}
}
