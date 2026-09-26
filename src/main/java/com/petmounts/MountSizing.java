package com.petmounts;

/**
 * Works out how much to enlarge a pet so it's big enough to ride without becoming giant.
 */
final class MountSizing
{
	private MountSizing()
	{
	}

	/**
	 * @param naturalHeight   pet height in local units as the game normally draws it
	 * @param targetHeight    height small pets should grow to
	 * @param maxGrowth       cap on enlargement (e.g. 4.5 = 450%)
	 * @param userMultiplier  final user tweak (1.0 = no change)
	 * @return scale factor to apply to the pet model (1.0 = unchanged)
	 */
	static float growthFactor(int naturalHeight, int targetHeight, float maxGrowth, float userMultiplier)
	{
		float auto = targetHeight / (float) Math.max(1, naturalHeight);
		// Never shrink a pet that's already big enough, never blow a tiny one up past the cap.
		auto = Math.max(1f, Math.min(maxGrowth, auto));
		return Math.max(0.25f, auto * userMultiplier);
	}
}
