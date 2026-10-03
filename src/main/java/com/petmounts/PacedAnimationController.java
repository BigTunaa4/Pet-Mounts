package com.petmounts;

import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;

/**
 * Plays an animation faster or slower. Used when running on a pet that has no run animation: its walk plays
 * at running pace, so its legs keep up with the ground instead of sliding.
 */
final class PacedAnimationController extends AnimationController
{
	private float pace = 1f;
	private float carry;
	/** Client ticks played so far, for {@link #cycle()}. */
	private float played;
	private final int length;

	PacedAnimationController(Client client, Animation animation)
	{
		super(client, animation);
		this.length = lengthOf(animation);
	}

	private static int lengthOf(Animation animation)
	{
		if (animation == null)
		{
			return 0;
		}
		if (animation.isMayaAnim())
		{
			return animation.getDuration();
		}
		int total = 0;
		int[] lengths = animation.getFrameLengths();
		if (lengths != null)
		{
			for (int l : lengths)
			{
				total += Math.max(0, l);
			}
		}
		return total;
	}

	/** How far through one loop of the animation it is, 0 to 1. Smooth, for timing the rider's sway. */
	float cycle()
	{
		return length <= 0 ? 0 : (played % length) / length;
	}

	/** Whether the animation has played all the way through at least once. */
	boolean playedOnce()
	{
		return length <= 0 || played >= length;
	}

	void setPace(float pace)
	{
		this.pace = Math.max(0.25f, Math.min(3f, pace));
	}

	@Override
	public void tick(int ticks)
	{
		played += ticks * pace;
		if (length > 0 && played > length * 1000f)
		{
			played %= length;
		}
		carry += ticks * pace;
		int whole = (int) carry;
		carry -= whole;
		if (whole > 0)
		{
			super.tick(whole);
		}
	}
}
