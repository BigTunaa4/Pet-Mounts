package com.petmounts;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(PetMountsConfig.GROUP)
public interface PetMountsConfig extends Config
{
	String GROUP = "petmounts";

	@ConfigSection(
		name = "Mount size",
		description = "How big your pet gets while you ride it",
		position = 10
	)
	String sizeSection = "size";

	@ConfigSection(
		name = "Rider",
		description = "How your character sits on the mount",
		position = 20
	)
	String riderSection = "rider";

	@ConfigSection(
		name = "Effects",
		description = "The climb-on animation and poof",
		position = 25
	)
	String effectsSection = "effects";

	@ConfigItem(
		keyName = "mountEffects",
		name = "Mount-up animation",
		description = "Beckon your pet with sparkles for about a second, then appear on it in a poof",
		position = 26,
		section = effectsSection
	)
	default boolean mountEffects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "matchPetColors",
		name = "Match pet colours",
		description = "Colour the sparkles and poof to match the pet you're riding",
		position = 27,
		section = effectsSection
	)
	default boolean matchPetColors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "effectColor",
		name = "Effect colour",
		description = "Colour used for the effects when 'Match pet colours' is off",
		position = 28,
		section = effectsSection
	)
	default Color effectColor()
	{
		return new Color(0xA64DFF);
	}

	@ConfigSection(
		name = "Which pets",
		description = "Only your own pets can be ridden, and awkward ones (objects, humanoids, snakes, fish) are refused",
		position = 30,
		closedByDefault = true
	)
	String petsSection = "pets";

	@ConfigItem(
		keyName = "alwaysAllow",
		name = "Always allow",
		description = "Pet names you want to ride even if they're normally refused, separated by commas",
		position = 31,
		section = petsSection
	)
	default String alwaysAllow()
	{
		return "";
	}

	@ConfigItem(
		keyName = "neverAllow",
		name = "Never allow",
		description = "Pet names you never want to ride, separated by commas",
		position = 32,
		section = petsSection
	)
	default String neverAllow()
	{
		return "";
	}

	// ---------- General ----------

	@ConfigItem(
		keyName = "mountHotkey",
		name = "Mount / dismount hotkey",
		description = "Press to hop on or off your pet",
		position = 1
	)
	default Keybind mountHotkey()
	{
		return new Keybind(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "showMenuOptions",
		name = "Right-click 'Ride' option",
		description = "Adds 'Ride' to your pet's right-click menu. 'Dismount' is always in the right-click menu while riding",
		position = 2
	)
	default boolean showMenuOptions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hopOffForActions",
		name = "Hop off for actions",
		description = "Step off the mount while fighting, skilling or teleporting, then climb back on automatically",
		position = 3
	)
	default boolean hopOffForActions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "remountOnLogin",
		name = "Stay mounted between sessions",
		description = "If you were riding when you logged out, climb back on when your pet appears",
		position = 4
	)
	default boolean remountOnLogin()
	{
		return true;
	}

	// ---------- Size ----------

	@Range(min = 80, max = 260)
	@Units(" units")
	@ConfigItem(
		keyName = "targetHeight",
		name = "Mount height",
		description = "How tall a small pet grows to while ridden (a player is roughly 200 units tall, a tile is 128). "
			+ "Pets already taller than this keep their normal size.",
		position = 11,
		section = sizeSection
	)
	default int targetHeight()
	{
		return 140;
	}

	@Range(min = 100, max = 800)
	@Units("%")
	@ConfigItem(
		keyName = "maxGrowth",
		name = "Max growth",
		description = "Upper limit on how much a tiny pet (pet rock, baby mole) can be enlarged",
		position = 12,
		section = sizeSection
	)
	default int maxGrowth()
	{
		return 450;
	}

	@Range(min = 50, max = 200)
	@Units("%")
	@ConfigItem(
		keyName = "sizeMultiplier",
		name = "Size tweak",
		description = "Final multiplier applied on top of the automatic size",
		position = 13,
		section = sizeSection
	)
	default int sizeMultiplier()
	{
		return 100;
	}

	// ---------- Rider ----------

	@ConfigItem(
		keyName = "riderPose",
		name = "Riding pose",
		description = "How you sit. Automatic picks Saddle, Wide (broad pets) or Cross-legged (floating pets)",
		position = 21,
		section = riderSection
	)
	default RiderPose riderPose()
	{
		return RiderPose.AUTO;
	}

	@Range(min = 20, max = 120)
	@Units("%")
	@ConfigItem(
		keyName = "seatHeight",
		name = "Seat height",
		description = "Where you sit, as a percentage of the mount's height",
		position = 23,
		section = riderSection
	)
	default int seatHeight()
	{
		return 62;
	}

	@Range(min = -64, max = 64)
	@ConfigItem(
		keyName = "seatForward",
		name = "Seat forward/back",
		description = "Slide the rider toward the mount's head (+) or tail (-)",
		position = 24,
		section = riderSection
	)
	default int seatForward()
	{
		return 0;
	}

	// ---------- Hidden state ----------

	@ConfigItem(
		keyName = "wasMounted",
		name = "",
		description = "",
		hidden = true
	)
	default boolean wasMounted()
	{
		return false;
	}

	@ConfigItem(
		keyName = "wasMounted",
		name = "",
		description = "",
		hidden = true
	)
	void wasMounted(boolean mounted);
}
