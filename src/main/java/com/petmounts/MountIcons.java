package com.petmounts;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

/**
 * A small picture of every mount in the Mount list, drawn from the game's own models in their idle pose.
 *
 * Each picture is its own small file (mount_icons/0.png, 1.png...), numbered in the order of the names in
 * mount_icons.txt. A picture is only loaded the first time the list shows it, so the list costs almost nothing
 * until it's opened.
 */
@Slf4j
final class MountIcons
{
	static final int WIDTH = 64;
	static final int HEIGHT = 40;

	/** Picture number for each mount name. */
	private final Map<String, Integer> numbers = new HashMap<>();
	private final Map<String, ImageIcon> loaded = new HashMap<>();

	MountIcons()
	{
		InputStream in = MountIcons.class.getResourceAsStream("mount_icons.txt");
		if (in == null)
		{
			return;
		}
		try (BufferedReader names = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String name;
			int i = 0;
			while ((name = names.readLine()) != null)
			{
				if (!name.isEmpty())
				{
					numbers.put(name, i);
				}
				i++;
			}
		}
		catch (IOException e)
		{
			log.debug("Couldn't read the mount picture list", e);
		}
	}

	/** The picture of this mount (by its name in the Mount list), or null if there isn't one. */
	ImageIcon get(String name)
	{
		if (name == null)
		{
			return null;
		}
		ImageIcon icon = loaded.get(name);
		if (icon != null || loaded.containsKey(name))
		{
			return icon;
		}
		Integer number = numbers.get(name);
		if (number != null)
		{
			try
			{
				BufferedImage image = ImageUtil.loadImageResource(MountIcons.class, "mount_icons/" + number + ".png");
				icon = image == null ? null : new ImageIcon(image);
			}
			catch (RuntimeException e)
			{
				log.debug("Couldn't load the picture of {}", name, e);
			}
		}
		loaded.put(name, icon);
		return icon;
	}
}
