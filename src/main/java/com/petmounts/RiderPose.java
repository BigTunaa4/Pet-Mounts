package com.petmounts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.AnimationID;

/**
 * How the rider sits on the mount.
 *
 * The contact point is where the rider touches the mount's back, measured from the player model posed in
 * each animation: its height above the player's feet, and how far behind the player's origin it is.
 * The poses follow the choices tested in Rapid Mounts by RapidUrsa (BSD 2-Clause); see THIRD_PARTY_NOTICES.md.
 */
@Getter
@RequiredArgsConstructor
public enum RiderPose
{
	/** Uses the pose tuned for each pet. */
	AUTO("Automatic", -1, -1, -1, 0, 0),
	/** Sitting upright as if on a stool, knees forward. */
	SADDLE("Saddle", AnimationID.CHAIR_SIT_READY_STOOL_1, -1, -1, 70, 0),
	/** Legs down both sides of the back, like riding a horse. Holds the last frame of the pose. */
	WIDE("Wide", AnimationID.BDAY17_STYLE, 31, 31, 97, -8),
	/** Low crouch with knees spread, for broad backs. */
	EXTRA_WIDE("Extra wide", AnimationID.TROLLROMANCE_TOBOGGAN_READY, -1, -1, 50, 36),
	/** Sitting cross-legged on top, for floating and rounded pets. Loops the settled part of the sit emote. */
	CROSS_LEGGED("Cross-legged", AnimationID.EMOTE_SIT_LOOP, 9, 16, 2, -11),
	/** Your normal standing pose (legs stop walking). */
	STANDING("Standing", -1, -1, -1, 0, 0);

	private final String displayName;
	private final int animationId;
	/** First frame to show, or -1 to play the whole animation. */
	private final int loopStart;
	/** Frame to loop back from (exclusive); equal to loopStart to hold a single frame. */
	private final int loopEnd;
	/** Height of the contact point above the player's feet, in local units. */
	private final int contactHeight;
	/** How far the contact point sits toward the player's back (model z), in local units. */
	private final int contactBack;

	boolean controlsFrames()
	{
		return loopStart >= 0;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
