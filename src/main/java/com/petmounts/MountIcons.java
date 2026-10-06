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
 * The pictures are one sheet (mount_icons.png), in the order of the names in mount_icons.txt, left to right and
 * top to bottom.
 */
@Slf4j
final class MountIcons
{
	static final int WIDTH = 40;
	static final int HEIGHT = 25;
	private static final int COLUMNS = 16;

	private final Map<String, ImageIcon> icons = new HashMap<>();

	MountIcons()
	{
		try
		{
			BufferedImage sheet = ImageUtil.loadImageResource(MountIcons.class, "mount_icons.png");
			InputStream in = MountIcons.class.getResourceAsStream("mount_icons.txt");
			if (sheet == null || in == null)
			{
				return;
			}
			try (BufferedReader names = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				String name;
				int i = 0;
				while ((name = names.readLine()) != null)
				{
					int x = (i % COLUMNS) * WIDTH, y = (i / COLUMNS) * HEIGHT;
					i++;
					if (name.isEmpty() || x + WIDTH > sheet.getWidth() || y + HEIGHT > sheet.getHeight())
					{
						continue;
					}
					icons.put(name, new ImageIcon(sheet.getSubimage(x, y, WIDTH, HEIGHT)));
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Couldn't load the mount pictures", e);
		}
	}

	/** The picture of this mount (by its name in the Mount list), or null if there isn't one. */
	ImageIcon get(String name)
	{
		return name == null ? null : icons.get(name);
	}
}
