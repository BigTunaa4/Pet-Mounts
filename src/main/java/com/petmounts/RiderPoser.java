package com.petmounts;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Player;

/**
 * Puts a player in a riding pose by swapping their movement animations for the pose, on your screen only, and
 * puts their own animations back afterwards.
 */
final class RiderPoser
{
	/** Frame counts of the pose animations, so held frames stay in range. */
	private final Map<Integer, Integer> frameCounts = new HashMap<>();

	private final Client client;
	/** The player's own movement animations while they're posed, or null. */
	private int[] saved;
	/** The pose animation last applied, and the frames it holds or loops, for {@link #hold}. */
	private int heldAnimation = -1;
	private int heldStart, heldEnd;
	private boolean controlsFrames;

	RiderPoser(Client client)
	{
		this.client = client;
	}

	/** Applies the pose. Cheap to call every tick. */
	void apply(Player player, RiderPose pose)
	{
		int anim = animationFor(player, pose);

		// If the game has reset the player's movement anims (e.g. after an equipment change),
		// capture the fresh set so we restore the right ones afterwards.
		if (saved == null || player.getWalkAnimation() != anim)
		{
			saved = new int[]{
				player.getIdlePoseAnimation(),
				player.getWalkAnimation(),
				player.getRunAnimation(),
				player.getIdleRotateLeft(),
				player.getIdleRotateRight(),
				player.getWalkRotateLeft(),
				player.getWalkRotateRight(),
				player.getWalkRotate180(),
			};
			anim = animationFor(player, pose);
		}

		player.setIdlePoseAnimation(anim);
		player.setWalkAnimation(anim);
		player.setRunAnimation(anim);
		player.setIdleRotateLeft(anim);
		player.setIdleRotateRight(anim);
		player.setWalkRotateLeft(anim);
		player.setWalkRotateRight(anim);
		player.setWalkRotate180(anim);

		int start = 0, end = 0;
		if (pose.controlsFrames())
		{
			int last = Math.max(0, frameCount(anim) - 1);
			start = Math.min(pose.getLoopStart(), last);
			end = Math.min(pose.getLoopEnd(), last);
		}
		if (player.getPoseAnimation() != anim)
		{
			player.setPoseAnimation(anim);
			player.setPoseAnimationFrame(start);
		}

		heldAnimation = anim;
		heldStart = start;
		heldEnd = end;
		controlsFrames = pose.controlsFrames();
		hold(player);
	}

	/**
	 * Makes sure the player is in the pose right now. Called just before the rider is drawn, because the game
	 * can move the player's animation on between ticks (for example when they start or stop walking), which
	 * would otherwise show for a frame as a twitch.
	 */
	void hold(Player player)
	{
		if (saved == null || heldAnimation == -1)
		{
			return;
		}
		if (player.getPoseAnimation() != heldAnimation)
		{
			player.setPoseAnimation(heldAnimation);
			player.setPoseAnimationFrame(heldStart);
		}
		// Some seated poses come from one-off emotes: hold one frame, or loop just the settled part.
		if (controlsFrames)
		{
			int frame = player.getPoseAnimationFrame();
			boolean single = heldStart >= heldEnd;
			if (single ? frame != heldStart : frame < heldStart || frame >= heldEnd)
			{
				player.setPoseAnimationFrame(heldStart);
			}
		}
	}

	/** Puts the player's own animations back. */
	void restore(Player player, boolean moving)
	{
		if (player == null || saved == null)
		{
			saved = null;
			return;
		}
		player.setIdlePoseAnimation(saved[0]);
		player.setWalkAnimation(saved[1]);
		player.setRunAnimation(saved[2]);
		player.setIdleRotateLeft(saved[3]);
		player.setIdleRotateRight(saved[4]);
		player.setWalkRotateLeft(saved[5]);
		player.setWalkRotateRight(saved[6]);
		player.setWalkRotate180(saved[7]);
		player.setPoseAnimation(moving ? saved[1] : saved[0]);
		player.setPoseAnimationFrame(0);
		saved = null;
		heldAnimation = -1;
	}

	/** Forgets the saved animations without touching the player (after logout or a world hop). */
	void forget()
	{
		saved = null;
		heldAnimation = -1;
	}

	private int animationFor(Player player, RiderPose pose)
	{
		if (pose == RiderPose.STANDING || pose.getAnimationId() == -1)
		{
			return saved != null ? saved[0] : player.getIdlePoseAnimation();
		}
		return pose.getAnimationId();
	}

	private int frameCount(int animationId)
	{
		Integer known = frameCounts.get(animationId);
		if (known != null)
		{
			return known;
		}
		Animation a = client.loadAnimation(animationId);
		if (a == null)
		{
			// Not loaded yet: don't remember that, or seated poses would be stuck on their first
			// (standing) frame. Leave the frames as they are until it loads.
			return Integer.MAX_VALUE;
		}
		int count = a.isMayaAnim() ? Math.max(1, a.getDuration()) : Math.max(1, a.getNumFrames());
		frameCounts.put(animationId, count);
		return count;
	}
}
