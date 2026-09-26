package com.petmounts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.AnimationID;

/**
 * How the rider sits on the mount.
 *
 * The seated poses (and the frames used from them) follow the tested choices in
 * Rapid Mounts by RapidUrsa (BSD 2-Clause); see THIRD_PARTY_NOTICES.md.
 */
@Getter
@RequiredArgsConstructor
public enum RiderPose
{
	/** Picks Saddle, Wide or Cross-legged from the pet's shape. */
	AUTO("Automatic", -1, -1, -1, 0),
	/** Upright, legs forward, like sitting in a saddle. */
	SADDLE("Saddle", AnimationID.CHAIR_SIT_READY_STOOL_1, -1, -1, 80),
	/** Legs apart for broad-backed pets. Holds a single frame of the pose. */
	WIDE("Wide", AnimationID.BDAY17_STYLE, 32, 32, 90),
	/** Cross-legged, for floating and flat pets. Loops the settled part of the sit emote. */
	CROSS_LEGGED("Cross-legged", AnimationID.EMOTE_SIT_LOOP, 9, 32, 0),
	/** Your normal standing pose (legs stop walking). */
	STANDING("Standing", -1, -1, -1, 0);

	private final String displayName;
	private final int animationId;
	/** First frame to play, or -1 to play the whole animation. */
	private final int loopStart;
	/** Frame to loop back from; equal to loopStart to hold a single frame. */
	private final int loopEnd;
	/**
	 * How high the pose already lifts the hips above the player's feet, in local units.
	 * Chair poses sit the body up as if on a stool; floor poses sit at foot level. Estimated from
	 * the difference between Rapid Mounts' tuned rider heights for the same mount in each pose.
	 */
	private final int hipHeight;

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
