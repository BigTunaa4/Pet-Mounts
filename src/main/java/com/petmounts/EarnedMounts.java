package com.petmounts;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * "Earned mounts" (optional): boss creatures in the Mount list stay locked until you've beaten that boss.
 *
 * Kill counts come from the game's own "Your Araxxor kill count is: 12." messages, saved per account. Kill counts
 * the Chat Commands plugin already saved for you count too, so players who have killed a boss before turning
 * this on don't have to kill it again. Purely cosmetic: it only decides which mounts you can pick.
 */
final class EarnedMounts
{
	/** Saved kill counts live under this config key prefix, per account. */
	private static final String KC_KEY = "kc_";
	/** Where the Chat Commands plugin keeps kill counts, per account, by lower-case boss name. */
	private static final String CHAT_COMMANDS_GROUP = "killcount";

	/** "Your Vorkath kill count is: 5." and, for raids, "Your completed Fractured Archive count is: 1." */
	private static final Pattern KILL_COUNT = Pattern.compile("Your (?:completed )?(?:the )?(.+?) (?:kill )?count is: ([\\d,]+)\\.?",
		Pattern.CASE_INSENSITIVE);

	/** Raids: their bosses unlock together, by finishing the raid once. */
	private static final String FRACTURED_ARCHIVE = "Fractured Archive";

	/** Boss mounts, by name in the Mount list, and the boss whose kill count unlocks each. */
	private static final Map<String, String> BOSSES = ImmutableMap.<String, String>builder()
		.put("Alchemical Hydra", "Alchemical Hydra")
		.put("Archive Guardian", FRACTURED_ARCHIVE)
		.put("Bork (Fractured Archive)", FRACTURED_ARCHIVE)
		.put("Dagon'hai Elder", FRACTURED_ARCHIVE)
		.put("Dreadnoth the Jellied", FRACTURED_ARCHIVE)
		.put("Ezekiel", FRACTURED_ARCHIVE)
		.put("Moia", FRACTURED_ARCHIVE)
		.put("Ocellus", FRACTURED_ARCHIVE)
		.put("Ocellus (second form)", FRACTURED_ARCHIVE)
		.put("The Smorks", FRACTURED_ARCHIVE)
		.put("Xarth", FRACTURED_ARCHIVE)
		.put("Araxxor", "Araxxor")
		.put("Artio", "Artio")
		.put("Callisto", "Callisto")
		.put("Cerberus", "Cerberus")
		.put("Giant Mole", "Giant Mole")
		.put("Kalphite Queen", "Kalphite Queen")
		.put("King Black Dragon", "King Black Dragon")
		.put("Manticore", "Sol Heredit")
		.put("Sarachnis", "Sarachnis")
		.put("Shellbane gryphon", "Shellbane Gryphon")
		.put("Spindel", "Spindel")
		.put("Venenatis", "Venenatis")
		.put("Vorkath", "Vorkath")
		.build();

	/** Quest bosses: beaten by finishing the quest. */
	private static final Map<String, Quest> QUESTS = ImmutableMap.of(
		"Elvarg", Quest.DRAGON_SLAYER_I
	);

	private final Client client;
	private final ConfigManager configManager;

	EarnedMounts(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	/** Every mount name that can be locked (for tests and the README). */
	static Iterable<String> lockable()
	{
		return com.google.common.collect.Iterables.concat(BOSSES.keySet(), QUESTS.keySet());
	}

	/**
	 * Why this mount is still locked, or null if it can be ridden. Call on the client thread (quest checks run
	 * a game script).
	 */
	String lockReason(String mountName)
	{
		if (mountName == null)
		{
			return null;
		}
		String boss = BOSSES.get(mountName);
		if (boss != null)
		{
			if (killCount(boss) > 0)
			{
				return null;
			}
			return FRACTURED_ARCHIVE.equals(boss) ? "Complete the Fractured Archive once to unlock this mount."
				: "Kill " + boss + " once to unlock this mount.";
		}
		Quest quest = QUESTS.get(mountName);
		if (quest != null && quest.getState(client) != QuestState.FINISHED)
		{
			return "Finish " + quest.getName() + " to unlock this mount.";
		}
		return null;
	}

	/** Reads a kill count message from the game and saves it. True if it was one. */
	boolean onGameMessage(String message)
	{
		Matcher m = KILL_COUNT.matcher(Text.removeTags(message));
		if (!m.matches())
		{
			return false;
		}
		int count;
		try
		{
			count = Integer.parseInt(m.group(2).replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return false;
		}
		configManager.setRSProfileConfiguration(PetMountsConfig.GROUP, KC_KEY + key(m.group(1)), count);
		return true;
	}

	/** The boss mount this kill count unlocks right now (name in the Mount list), or null. */
	static String unlockedBy(String message)
	{
		Matcher m = KILL_COUNT.matcher(Text.removeTags(message));
		if (!m.matches() || !"1".equals(m.group(2)))
		{
			return null;
		}
		String boss = m.group(1).trim();
		if (FRACTURED_ARCHIVE.equalsIgnoreCase(boss))
		{
			return "the Fractured Archive bosses";
		}
		for (Map.Entry<String, String> e : BOSSES.entrySet())
		{
			if (e.getValue().equalsIgnoreCase(boss))
			{
				return e.getKey();
			}
		}
		return null;
	}

	private int killCount(String boss)
	{
		Integer own = configManager.getRSProfileConfiguration(PetMountsConfig.GROUP, KC_KEY + key(boss), Integer.class);
		if (own != null && own > 0)
		{
			return own;
		}
		Integer saved = configManager.getRSProfileConfiguration(CHAT_COMMANDS_GROUP, boss.toLowerCase(), Integer.class);
		return saved != null ? saved : 0;
	}

	static String key(String boss)
	{
		return boss.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_");
	}
}
