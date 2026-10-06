package com.petmounts;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.SpotanimID;

/**
 * The extras around your mount: its sounds, the effects that go with its tricks (a dragon's fire breath), and the
 * trails showpiece mounts leave behind. Effects are the game's own graphics; sounds are the creature's own.
 */
@Singleton
class MountFlair
{
	/** Flames rising from the ground. */
	private static final int FLAMES = SpotanimID.FIREWARRIOR_FLAMES;
	/** A small pool of lava. */
	private static final int LAVA = SpotanimID.FIRE_LIQUID;
	/** A soft puff of smoke, recoloured for mist. */
	private static final int MIST = SpotanimID.SMALL_SMOKEPUFF;

	/** Pale icy blue and ghostly green, as the game's HSL colours. */
	private static final short ICE = SaddleMesh.hsl(40, 3, 100);
	private static final short SPIRIT = SaddleMesh.hsl(22, 4, 80);

	/** The sound of digging, from the dogs' own dig animation. */
	private static final int DIG_SOUND = 12114;
	/** Dragons' fire breath. */
	private static final int FIRE_BREATH = 81;
	/** Skeletal wyverns' icy breath. */
	private static final int ICE_BREATH = 2988;

	/** Client ticks (20 ms each) between a mount's own sounds: 30 to 60 seconds. */
	private static final int SOUND_MIN = 1500, SOUND_SPREAD = 1500;

	private final Client client;
	private final MountEffects effects;

	private int ticks;
	private int nextSoundAt;
	private int nextTrailAt;
	private int nextFootprintAt;
	/** A breath effect waiting for the mount to open its mouth: the graphic, or -1, and when. */
	private int breath = -1;
	private int breathAt;

	@Inject
	MountFlair(Client client, MountEffects effects)
	{
		this.client = client;
		this.effects = effects;
	}

	/** Starts over, e.g. on climbing on, so a sound doesn't come straight away. */
	void reset()
	{
		nextSoundAt = ticks + SOUND_MIN / 2 + (int) (Math.random() * SOUND_SPREAD);
		breath = -1;
	}

	/**
	 * Call every client tick while riding.
	 *
	 * @param gait 0 standing, 1 walking, 2 running
	 */
	void tick(MountRig rig, Player me, int gait, boolean sounds, boolean trails)
	{
		ticks++;
		int npcId = rig.npcId();

		int trick = rig.takeTrickStarted();
		if (trick != -1)
		{
			onTrick(trick, npcId, sounds, trails);
		}
		if (breath != -1 && ticks >= breathAt)
		{
			// Breath comes out in front of the mount, at about head height.
			spawnAround(rig, me, breath, breath == FLAMES ? (short) 0 : ICE, 0, -1.1f, rig.mountHeight() / 2,
				flameScale(rig) + 20);
			breath = -1;
		}

		if (sounds && ticks >= nextSoundAt)
		{
			play(MountExtras.sound(npcId));
			nextSoundAt = ticks + SOUND_MIN + (int) (Math.random() * SOUND_SPREAD);
		}

		MountExtras.Trail trail = trails ? MountExtras.trail(rig.petName()) : null;
		if (trail != null)
		{
			trail(rig, me, trail, gait);
		}
	}

	private void onTrick(int trick, int npcId, boolean sounds, boolean trails)
	{
		if (sounds)
		{
			if (trick == MountExtras.DIG || trick == MountExtras.DIG_SMALL)
			{
				play(DIG_SOUND);
			}
			else if (Math.random() < 0.6)
			{
				play(MountExtras.sound(npcId));
			}
		}
		if (trails && trick == FIRE_BREATH)
		{
			breath = FLAMES;
			breathAt = ticks + 30; // as the dragon's head comes forward
		}
		else if (trails && trick == ICE_BREATH)
		{
			breath = MIST;
			breathAt = ticks + 20;
		}
	}

	private void trail(MountRig rig, Player me, MountExtras.Trail trail, int gait)
	{
		boolean moving = gait > 0;
		if (ticks >= nextTrailAt)
		{
			switch (trail)
			{
				case FIRE:
				case LAVA:
					spawnAround(rig, me, FLAMES, (short) 0, randomSide(), randomSide(), 0, flameScale(rig));
					nextTrailAt = ticks + (moving ? 9 : 45) + (int) (Math.random() * 8);
					break;
				case FROST:
					spawnAround(rig, me, MIST, ICE, randomSide(), randomSide(), 0, 128);
					nextTrailAt = ticks + (moving ? 10 : 40) + (int) (Math.random() * 8);
					break;
				case GHOST:
					spawnAround(rig, me, MIST, SPIRIT, randomSide(), randomSide(), 0, 128);
					nextTrailAt = ticks + (moving ? 10 : 40) + (int) (Math.random() * 8);
					break;
			}
		}
		if (trail == MountExtras.Trail.LAVA && moving && ticks >= nextFootprintAt)
		{
			// Glowing footprints left behind on the ground.
			spawnAround(rig, me, LAVA, (short) 0, randomSide() * 0.5f, 0.6f, 0, 70);
			nextFootprintAt = ticks + 18;
		}
	}

	/** Bigger mounts, bigger flames: a third to two thirds of the game's size. */
	private static int flameScale(MountRig rig)
	{
		return Math.max(40, Math.min(85, rig.mountHeight() / 3));
	}

	private static float randomSide()
	{
		return (float) (Math.random() * 1.2 - 0.6);
	}

	/**
	 * Plays a graphic at a spot on the mount.
	 *
	 * @param across sideways, -1 to 1 of the mount's half width
	 * @param along  along its length, -1 (front) to 1 (back) of its half length
	 */
	private void spawnAround(MountRig rig, Player me, int graphic, short color, float across, float along,
		int height, int scale)
	{
		LocalPoint at = rig.drawnAt();
		if (at == null || me.getWorldView() == null)
		{
			return;
		}
		float mx = across * rig.halfWidth(), mz = along * rig.halfLength();
		int orientation = me.getCurrentOrientation();
		double rad = orientation * Math.PI / 1024.0;
		double sin = Math.sin(rad), cos = Math.cos(rad);
		int dx = (int) Math.round(mx * cos + mz * sin);
		int dy = (int) Math.round(mz * cos - mx * sin);
		LocalPoint spot = new LocalPoint(at.getX() + dx, at.getY() + dy, at.getWorldView());
		effects.spawnAt(graphic, color, spot, me.getWorldView().getPlane(), height, orientation, scale);
	}

	/** Plays one of the mount's sounds quietly, following the game's sound effect volume (and mute). */
	private void play(int sound)
	{
		if (sound < 0 || client.getPreferences() == null)
		{
			return;
		}
		int volume = client.getPreferences().getSoundEffectVolume();
		if (volume <= 0)
		{
			return; // sound effects are muted
		}
		client.playSoundEffect(sound, Math.max(1, volume * 2 / 3));
	}
}
