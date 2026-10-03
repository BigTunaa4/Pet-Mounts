package com.petmounts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.RuneLiteObject;

/**
 * The enlarged pet. It animates the pet at its own size and enlarges each animation frame afterwards, the way the
 * game draws scaled NPCs. Enlarging the model first and animating it after works for simple animations but tears
 * newer, skeletal ones apart (they move bones by fixed distances), which stretched pets like Scurry, Gull and the
 * Callisto cub across the screen.
 */
@Slf4j
final class MountObject extends RuneLiteObject
{
	/** The enlarged pet standing still, for when there's no animation. */
	private final Model still;
	/** The model being animated (the pet at its own size, or the enlarged one). */
	private final Model animated;
	private final int scaleX, scaleY;
	private final int hover;
	/** For floating pets: the seat (triangle and weights) and its height at rest, to hold it at riding height. */
	private final boolean holdHeight;
	private final int seatA, seatB, seatC;
	private final float seatWa, seatWb, seatWc;
	private final float restSeatY;
	/** How far a floating pet may bob above or below its riding height. */
	private static final float BOB = 8;

	MountObject(Client client, PetModels.Built built)
	{
		super(client);
		this.still = built.model;
		if (built.base != null)
		{
			animated = built.base;
			scaleX = Math.max(1, Math.round(PetModels.SCALE_BASE * built.scaleX));
			scaleY = Math.max(1, Math.round(PetModels.SCALE_BASE * built.scaleY));
			hover = Math.round(built.hover);
		}
		else
		{
			// Couldn't prepare the pet at its own size: animate the enlarged model as it is.
			animated = built.model;
			scaleX = scaleY = PetModels.SCALE_BASE;
			hover = 0;
		}
		setModel(animated);

		holdHeight = built.floating;
		seatA = built.a;
		seatB = built.b;
		seatC = built.c;
		seatWa = built.wa;
		seatWb = built.wb;
		seatWc = built.wc;
		restSeatY = seatY(still);
	}

	private float seatY(Model m)
	{
		float[] ys = m.getVerticesY();
		int n = m.getVerticesCount();
		if (seatA >= n || seatB >= n || seatC >= n)
		{
			return Float.NaN;
		}
		return seatWa * ys[seatA] + seatWb * ys[seatB] + seatWc * ys[seatC];
	}

	@Override
	public Model getModel()
	{
		// Called while the game draws the scene: never let a problem here reach the game.
		try
		{
			return animatedFrame();
		}
		catch (RuntimeException e)
		{
			log.debug("Couldn't animate the mount this frame", e);
			return still;
		}
	}

	private Model animatedFrame()
	{
		Model frame = super.getModel();
		if (frame == null || frame == animated)
		{
			// Not animating: the unanimated base must never be changed, so show the enlarged still model.
			return still;
		}
		if (scaleX != PetModels.SCALE_BASE || scaleY != PetModels.SCALE_BASE)
		{
			// The animated frame is a fresh copy made for this call, so it can be resized in place.
			frame.scale(scaleX, scaleY, scaleX);
		}
		if (hover != 0)
		{
			frame.translate(0, hover, 0);
		}
		boolean moved = scaleX != PetModels.SCALE_BASE || scaleY != PetModels.SCALE_BASE || hover != 0;
		if (holdHeight && !Float.isNaN(restSeatY))
		{
			// Flying pets' walk animations fly them up into the air. Keep the seat at riding height (with a
			// little bob) so you stay on its back and it doesn't soar above you.
			float drift = seatY(frame) - restSeatY; // y points down: negative is higher
			float fix = drift < -BOB ? -drift - BOB : drift > BOB ? -(drift - BOB) : 0;
			if (!Float.isNaN(fix) && Math.abs(fix) >= 1)
			{
				frame.translate(0, Math.round(fix), 0);
				moved = true;
			}
		}
		if (moved)
		{
			frame.calculateBoundsCylinder();
		}
		return frame;
	}
}
