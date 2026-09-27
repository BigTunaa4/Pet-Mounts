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

		// Some seated poses come from one-off emotes: hold one frame, or loop just the settled part.
		if (pose.controlsFrames())
		{
			int frame = player.getPoseAnimationFrame();
			boolean hold = start >= end;
			if (hold ? frame != start : frame < start || frame >= end)
			{
				player.setPoseAnimationFrame(start);
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
	}

	/** Forgets the saved animations without touching the player (after logout or a world hop). */
	void forget()
	{
		saved = null;
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
		return frameCounts.computeIfAbsent(animationId, id ->
		{
			Animation a = client.loadAnimation(id);
			if (a == null)
			{
				return 1;
			}
			return a.isMayaAnim() ? Math.max(1, a.getDuration()) : Math.max(1, a.getNumFrames());
		});
	}
}
