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
		name = "Mount and rider",
		description = "Size of the mount and how you sit on it",
		position = 10
	)
	String riderSection = "rider";

	@ConfigSection(
		name = "Effects",
		description = "The climb-on animation, tricks, sounds and trails",
		position = 20
	)
	String effectsSection = "effects";

	@ConfigSection(
		name = "Other players",
		description = "See other players riding their pets too",
		position = 25
	)
	String othersSection = "others";

	@ConfigSection(
		name = "Which pets",
		description = "Only your own pets can be ridden, and awkward ones (objects, humanoids, snakes, fish) are refused",
		position = 30,
		closedByDefault = true
	)
	String petsSection = "pets";

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
		keyName = "showMountButton",
		name = "On-screen mount button",
		description = "A small button to hop on and off. Hold Alt and drag to move it",
		position = 3
	)
	default boolean showMountButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hopOffForActions",
		name = "Hop off for actions",
		description = "Step off the mount while fighting, skilling or teleporting, then climb back on automatically",
		position = 4
	)
	default boolean hopOffForActions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "remountOnLogin",
		name = "Stay mounted between sessions",
		description = "If you were riding when you logged out, climb back on when your pet appears",
		position = 5
	)
	default boolean remountOnLogin()
	{
		return true;
	}

	// ---------- Mount and rider ----------

	@Range(min = 60, max = 160)
	@Units("%")
	@ConfigItem(
		keyName = "sizeMultiplier",
		name = "Mount size",
		description = "Every pet is sized so its back is about pony height. Make them all bigger or smaller here",
		position = 11,
		section = riderSection
	)
	default int sizeMultiplier()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "riderPose",
		name = "Riding pose",
		description = "How you sit. Automatic uses the pose chosen for each pet",
		position = 12,
		section = riderSection
	)
	default RiderPose riderPose()
	{
		return RiderPose.AUTO;
	}

	@Range(min = -40, max = 40)
	@ConfigItem(
		keyName = "seatHeightAdjust",
		name = "Seat height",
		description = "Raise (+) or lower (-) where you sit. Every pet has a measured seat; this is a fine-tune",
		position = 13,
		section = riderSection
	)
	default int seatHeightAdjust()
	{
		return 0;
	}

	@Range(min = -60, max = 60)
	@ConfigItem(
		keyName = "seatForwardAdjust",
		name = "Seat forward/back",
		description = "Slide where you sit toward the head (+) or tail (-)",
		position = 14,
		section = riderSection
	)
	default int seatForwardAdjust()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showSaddle",
		name = "Saddle and blanket",
		description = "Put a saddle and blanket on your mount, moulded to its back. The blanket matches your pet's colours",
		position = 15,
		section = riderSection
	)
	default boolean showSaddle()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showReins",
		name = "Reins",
		description = "Hold reins running to your mount's mouth. Pets you sit on top of, like floating ones, have none",
		position = 16,
		section = riderSection
	)
	default boolean showReins()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideHeldItems",
		name = "Hide weapon and shield",
		description = "Stops weapons and shields poking through your mount. On your screen only; they come back when you get off",
		position = 17,
		section = riderSection
	)
	default boolean hideHeldItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideCape",
		name = "Hide cape",
		description = "Stops capes hanging through your mount's back. On your screen only",
		position = 18,
		section = riderSection
	)
	default boolean hideCape()
	{
		return true;
	}

	@ConfigItem(
		keyName = "naturalMotion",
		name = "Natural riding motion",
		description = "Settle into the saddle, sway with your mount's stride and rock with it as it starts and stops",
		position = 19,
		section = riderSection
	)
	default boolean naturalMotion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "saddleStyle",
		name = "Saddle style",
		description = "The look of the saddle and blanket. Classic matches the blanket to your pet",
		position = 20,
		section = riderSection
	)
	default SaddleStyle saddleStyle()
	{
		return SaddleStyle.CLASSIC;
	}

	// ---------- Other players ----------

	@ConfigItem(
		keyName = "everyoneRides",
		name = "Everyone rides",
		description = "Show other players riding the pets following them, on your screen only. Right-click them as usual;"
			+ " hold Shift to see everyone normally. Always off in the Wilderness and on PvP worlds",
		position = 1,
		section = othersSection
	)
	default boolean everyoneRides()
	{
		return false;
	}

	@Range(min = 1, max = 2000)
	@ConfigItem(
		keyName = "everyoneRidesLimit",
		name = "Riders shown",
		description = "The most other players shown riding at once, nearest first. Set it high to show everyone; lower it if busy areas feel slow",
		position = 2,
		section = othersSection
	)
	default int everyoneRidesLimit()
	{
		return 10;
	}

	// ---------- Effects ----------

	@ConfigItem(
		keyName = "mountEffects",
		name = "Mount-up animation",
		description = "Beckon your pet with sparkles for about a second, then appear on it in a poof",
		position = 21,
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
		position = 22,
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
		position = 23,
		section = effectsSection
	)
	default Color effectColor()
	{
		return new Color(0xA64DFF);
	}

	@ConfigItem(
		keyName = "idleTricks",
		name = "Mount tricks",
		description = "Now and then, while you stand still, your mount does something: dogs dig, cats arch their backs, "
			+ "dragons rear up and breathe fire, cows graze. It also shows off when you climb on",
		position = 24,
		section = effectsSection
	)
	default boolean idleTricks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mountSounds",
		name = "Mount sounds",
		description = "Your mount makes its own sound now and then (a purr, a bark, a roar), quietly. "
			+ "Follows your sound effect volume",
		position = 25,
		section = effectsSection
	)
	default boolean mountSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mountTrails",
		name = "Special mount effects",
		description = "Showpiece mounts leave a trail: fiery ones drop little flames, the lava dragon leaves glowing "
			+ "footprints, Vorkath and frost dragons trail icy mist, and ghostly mounts a spectral mist",
		position = 26,
		section = effectsSection
	)
	default boolean mountTrails()
	{
		return true;
	}

	@ConfigItem(
		keyName = "randomMount",
		name = "Random mount each login",
		description = "Pick a random mount every time you log in: one of your favourites if you've starred any, "
			+ "or any mount if not",
		position = 27,
		section = effectsSection
	)
	default boolean randomMount()
	{
		return false;
	}

	// ---------- Which pets ----------

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

	@ConfigItem(
		keyName = "favouriteMounts",
		name = "",
		description = "Starred mounts (NPC ids), shown first in the Mount list. Set in the Mount Stable",
		hidden = true
	)
	default String favouriteMounts()
	{
		return "";
	}

	@ConfigItem(
		keyName = "chosenMount",
		name = "",
		description = "The pet to ride (its NPC id), or 0 to ride the pet following you. Set in the Mount Stable",
		hidden = true
	)
	default int chosenMount()
	{
		return 0;
	}
}
