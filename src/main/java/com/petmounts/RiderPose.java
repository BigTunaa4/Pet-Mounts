package com.petmounts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.AnimationID;

@Getter
@RequiredArgsConstructor
public enum RiderPose
{
	/** Cross-legged seat from the magic carpet ride. */
	SEATED("Seated", AnimationID.CARPET_FLYING),
	/** Keeps your normal standing idle pose (legs stop walking). */
	STANDING("Standing", -1);

	private final String displayName;
	private final int animationId;

	@Override
	public String toString()
	{
		return displayName;
	}
}
