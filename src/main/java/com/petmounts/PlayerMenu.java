package com.petmounts;

import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.client.util.Text;

/** Builds right-click entries for a player the way the game does: their options, name and combat level. */
final class PlayerMenu
{
	/** The game adds this to a menu type to push the option below "Walk here" (e.g. Attack outside PvP). */
	private static final int DEPRIORITIZED = 2000;

	private PlayerMenu()
	{
	}

	/** The menu action for one of the game's player option types, or null if it isn't a player option. */
	static MenuAction action(int type)
	{
		int id = type >= DEPRIORITIZED ? type - DEPRIORITIZED : type;
		if (id < MenuAction.PLAYER_FIRST_OPTION.getId() || id > MenuAction.PLAYER_EIGHTH_OPTION.getId())
		{
			return null;
		}
		return MenuAction.of(id);
	}

	static boolean deprioritized(int type)
	{
		return type >= DEPRIORITIZED;
	}

	/** "Name  (level-126)", coloured like the game: white name, level from green (lower) to red (higher). */
	static String target(Player player, int myLevel)
	{
		String name = player.getName() == null ? "" : Text.removeTags(player.getName());
		int level = player.getCombatLevel();
		return "<col=ffffff>" + name + levelColor(level - myLevel) + "  (level-" + level + ")";
	}

	static String levelColor(int difference)
	{
		if (difference < -9)
		{
			return "<col=00ff00>";
		}
		if (difference < -6)
		{
			return "<col=40ff00>";
		}
		if (difference < -3)
		{
			return "<col=80ff00>";
		}
		if (difference < 0)
		{
			return "<col=c0ff00>";
		}
		if (difference > 9)
		{
			return "<col=ff0000>";
		}
		if (difference > 6)
		{
			return "<col=ff4000>";
		}
		if (difference > 3)
		{
			return "<col=ff8000>";
		}
		if (difference > 0)
		{
			return "<col=ffc000>";
		}
		return "<col=ffff00>";
	}
}
