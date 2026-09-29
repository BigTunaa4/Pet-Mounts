package com.petmounts;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.RuneLiteObject;

/**
 * The enlarged pet. It animates the pet at its own size and enlarges each animation frame afterwards, the way the
 * game draws scaled NPCs. Enlarging the model first and animating it after works for simple animations but tears
 * newer, skeletal ones apart (they move bones by fixed distances), which stretched pets like Scurry, Gull and the
 * Callisto cub across the screen.
 */
final class MountObject extends RuneLiteObject
{
	/** The enlarged pet standing still, for when there's no animation. */
	private final Model still;
	/** The model being animated (the pet at its own size, or the enlarged one). */
	private final Model animated;
	private final int scaleX, scaleY;
	private final int hover;

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
	}

	@Override
	public Model getModel()
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
		if (scaleX != PetModels.SCALE_BASE || scaleY != PetModels.SCALE_BASE || hover != 0)
		{
			frame.calculateBoundsCylinder();
		}
		return frame;
	}
}
