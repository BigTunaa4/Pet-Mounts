package com.petmounts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The Mount Stable: a sidebar panel showing the pet following you, whether it can be ridden, a ride button,
 * the saddle, reins, motion and hide-items switches, and adjustments saved for this pet.
 *
 * Swing code only; every game action goes back to the plugin, which runs it on the client thread.
 */
class MountStablePanel extends PluginPanel
{
	interface Actions
	{
		void toggleRide();

		void saveTweaks(PetTweaks tweaks);

		void setOption(String key, boolean value);

		/** Rides this pet (NPC id), or the pet following you for 0. */
		void chooseMount(int npcId);

		/** Stars or unstars this mount (NPC id). */
		void toggleFavourite(int npcId);

		/** Picks a random mount: a favourite if any are starred. */
		void randomMount();

		void setSaddleStyle(SaddleStyle style);

		/** Copies details of the current mount, for a bug report. */
		void copyMountInfo();
	}

	/** What the panel shows; built by the plugin on the client thread. */
	static final class State
	{
		final String petName;
		final String status;
		final boolean canRide;
		final boolean riding;
		final PetTweaks tweaks;
		final boolean saddle;
		final boolean reins;
		final boolean motion;
		final boolean everyone;
		final boolean hideHeld;
		final boolean hideCape;
		/** The pet picked to ride (NPC id), or 0 for the pet following you. */
		final int chosen;
		/** Starred mounts (NPC ids). */
		final java.util.Set<Integer> favourites;
		final SaddleStyle saddleStyle;

		State(String petName, String status, boolean canRide, boolean riding, PetTweaks tweaks,
			boolean saddle, boolean reins, boolean motion, boolean everyone, boolean hideHeld, boolean hideCape, int chosen,
			java.util.Set<Integer> favourites, SaddleStyle saddleStyle)
		{
			this.favourites = favourites;
			this.saddleStyle = saddleStyle;
			this.petName = petName;
			this.status = status;
			this.canRide = canRide;
			this.riding = riding;
			this.tweaks = tweaks;
			this.saddle = saddle;
			this.reins = reins;
			this.motion = motion;
			this.everyone = everyone;
			this.hideHeld = hideHeld;
			this.hideCape = hideCape;
			this.chosen = chosen;
		}

		boolean sameAs(State o)
		{
			return o != null && java.util.Objects.equals(petName, o.petName) && status.equals(o.status)
				&& canRide == o.canRide && riding == o.riding && saddle == o.saddle && reins == o.reins && motion == o.motion && everyone == o.everyone && hideHeld == o.hideHeld
				&& hideCape == o.hideCape && chosen == o.chosen && favourites.equals(o.favourites)
				&& saddleStyle == o.saddleStyle && tweaks.size == o.tweaks.size && tweaks.seatHeight == o.tweaks.seatHeight
				&& tweaks.seatForward == o.tweaks.seatForward && tweaks.pose == o.tweaks.pose;
		}
	}

	private static final Color READY = new Color(110, 225, 110);
	private static final String TIP_URL = "https://cash.app/$VintageAdVenturesss";

	private final Actions actions;
	private final JLabel petLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JButton rideButton = new JButton();
	/** "Your pet" first, then every rideable pet by name. */
	private final JComboBox<String> mountBox = new JComboBox<>();
	private static final String YOUR_PET = "The pet following you";
	private final JCheckBox saddleBox = new JCheckBox("Saddle and blanket");
	private final JCheckBox reinsBox = new JCheckBox("Reins");
	private final JCheckBox motionBox = new JCheckBox("Natural riding motion");
	private final JCheckBox everyoneBox = new JCheckBox("Everyone rides (other players)");
	private final JCheckBox heldBox = new JCheckBox("Hide weapon and shield");
	private final JCheckBox capeBox = new JCheckBox("Hide cape");
	private final JComboBox<RiderPose> poseBox = new JComboBox<>(RiderPose.values());
	private final JComboBox<SaddleStyle> saddleStyleBox = new JComboBox<>(SaddleStyle.values());
	private final JButton favouriteButton = new JButton("Favourite");
	private final JButton randomButton = new JButton("Random");
	private final MountCell mountCell = new MountCell(new MountIcons());
	/** Favourites shown first in the Mount list, as last built. */
	private java.util.Set<Integer> listedFavourites = java.util.Collections.emptySet();
	private final JSlider sizeSlider = slider(60, 160, 100);
	private final JSlider heightSlider = slider(-40, 40, 0);
	private final JSlider forwardSlider = slider(-60, 60, 0);
	private final JPanel tweakPanel = new JPanel();
	private final JLabel tweakTitle = new JLabel();

	private boolean updating;
	private State shown;

	MountStablePanel(Actions actions)
	{
		this.actions = actions;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Mount Stable");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		content.add(left(title));
		content.add(Box.createRigidArea(new Dimension(0, 8)));

		// The pet following you and whether it can be ridden.
		JPanel card = new JPanel(new GridLayout(2, 1, 0, 2));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(8, 8, 8, 8));
		petLabel.setFont(FontManager.getRunescapeBoldFont());
		petLabel.setForeground(Color.WHITE);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		card.add(petLabel);
		card.add(statusLabel);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
		content.add(left(card));
		content.add(Box.createRigidArea(new Dimension(0, 8)));

		// What to ride: your follower, or any rideable pet or creature, even a pet you haven't got yet.
		content.add(left(small("Mount")));
		fillMountList(java.util.Collections.emptySet());
		mountBox.setRenderer(mountCell);
		// Size the list from one row, so pictures only load as rows come into view.
		mountBox.setPrototypeDisplayValue(YOUR_PET);
		mountBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, MountIcons.HEIGHT + 12));
		mountBox.setMaximumRowCount(6);
		mountBox.setToolTipText("Ride the pet following you, or pick any pet or creature to ride: dragons, unicorns, the battle tortoise, even a pet rock");
		mountBox.addActionListener(e ->
		{
			if (!updating)
			{
				Object picked = mountBox.getSelectedItem();
				Integer id = picked == null || YOUR_PET.equals(picked) ? null : MountFits.choices().get(picked);
				actions.chooseMount(id == null ? 0 : id);
			}
		});
		content.add(left(mountBox));
		content.add(Box.createRigidArea(new Dimension(0, 4)));

		// Star the mount you're on, or let fate pick one.
		JPanel picks = new JPanel(new GridLayout(1, 2, 4, 0));
		favouriteButton.setFocusPainted(false);
		favouriteButton.setToolTipText("Star this mount: favourites are listed first and in gold");
		favouriteButton.addActionListener(e ->
		{
			if (shown != null && shown.chosen > 0)
			{
				actions.toggleFavourite(shown.chosen);
			}
		});
		randomButton.setFocusPainted(false);
		randomButton.setToolTipText("Ride a random mount: one of your favourites if you've starred any");
		randomButton.addActionListener(e -> actions.randomMount());
		picks.add(favouriteButton);
		picks.add(randomButton);
		picks.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		content.add(left(picks));
		content.add(Box.createRigidArea(new Dimension(0, 6)));

		rideButton.setFont(FontManager.getRunescapeBoldFont());
		rideButton.setFocusPainted(false);
		rideButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		rideButton.addActionListener(e -> actions.toggleRide());
		content.add(left(rideButton));
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		// Switches shared by every pet.
		checkbox(saddleBox, "showSaddle");
		checkbox(reinsBox, "showReins");
		checkbox(motionBox, "naturalMotion");
		checkbox(everyoneBox, "everyoneRides");
		checkbox(heldBox, "hideHeldItems");
		checkbox(capeBox, "hideCape");
		content.add(left(saddleBox));
		content.add(left(reinsBox));
		content.add(left(motionBox));
		content.add(left(everyoneBox));
		content.add(left(heldBox));
		content.add(left(capeBox));
		content.add(Box.createRigidArea(new Dimension(0, 6)));
		content.add(left(small("Saddle style")));
		saddleStyleBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		saddleStyleBox.setToolTipText("The look of the saddle and blanket. Classic matches the blanket to your pet");
		saddleStyleBox.addActionListener(e ->
		{
			if (!updating && saddleStyleBox.getSelectedItem() != null)
			{
				actions.setSaddleStyle((SaddleStyle) saddleStyleBox.getSelectedItem());
			}
		});
		content.add(left(saddleStyleBox));
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		// Adjustments remembered for this pet.
		tweakPanel.setLayout(new BoxLayout(tweakPanel, BoxLayout.Y_AXIS));
		tweakPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tweakPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		tweakTitle.setFont(FontManager.getRunescapeSmallFont());
		tweakTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tweakPanel.add(left(tweakTitle));
		tweakPanel.add(Box.createRigidArea(new Dimension(0, 6)));
		tweakPanel.add(left(small("Riding pose")));
		poseBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		poseBox.addActionListener(e -> tweaksChanged());
		tweakPanel.add(left(poseBox));
		tweakPanel.add(labelled("Size (%)", sizeSlider));
		tweakPanel.add(labelled("Seat height", heightSlider));
		tweakPanel.add(labelled("Seat forward / back", forwardSlider));
		JButton reset = new JButton("Reset this pet");
		reset.setFocusPainted(false);
		reset.addActionListener(e -> actions.saveTweaks(PetTweaks.NONE));
		tweakPanel.add(Box.createRigidArea(new Dimension(0, 6)));
		tweakPanel.add(left(reset));
		content.add(left(tweakPanel));

		content.add(Box.createRigidArea(new Dimension(0, 10)));
		JLabel tip = new JLabel("<html>Right-click your pet and choose <b>Ride</b>, or use the hotkey (Alt + M by default)."
			+ " Right-click anywhere and choose <b>Dismount</b> to get off. Hold <b>Shift</b> to see other players"
			+ " normally.</html>");
		tip.setFont(FontManager.getRunescapeSmallFont());
		tip.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(left(tip));

		// Something look wrong? Copy the details to paste into a bug report.
		content.add(Box.createRigidArea(new Dimension(0, 8)));
		JLabel report = new JLabel("<html><u>Copy mount info</u> for a bug report</html>");
		report.setFont(FontManager.getRunescapeSmallFont());
		report.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		report.setToolTipText("Copies the mount, its size and seat settings to your clipboard, to paste into a report");
		report.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		report.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				actions.copyMountInfo();
			}
		});
		content.add(left(report));

		// Optional tip link. Nothing is locked behind it.
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		JLabel support = new JLabel("<html>Enjoying Pet Mounts? It's free, but you can <u>leave a tip</u>.</html>");
		support.setFont(FontManager.getRunescapeSmallFont());
		support.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		support.setToolTipText(TIP_URL);
		support.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		support.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(TIP_URL);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				support.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				support.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		content.add(left(support));

		add(content, BorderLayout.NORTH);
		show(new State(null, "Summon one of your pets, or pick a mount below.", false, false, PetTweaks.NONE, true, true, true, true, true, true, 0,
				java.util.Collections.emptySet(), SaddleStyle.CLASSIC));
	}

	/** Shows the latest state. Call on the Swing thread. */
	void show(State s)
	{
		if (s.sameAs(shown))
		{
			return;
		}
		shown = s;
		updating = true;
		petLabel.setText(s.petName == null ? "No pet following you" : s.petName);
		statusLabel.setText(s.status);
		statusLabel.setForeground(s.riding || s.canRide ? READY : ColorScheme.LIGHT_GRAY_COLOR);
		rideButton.setText(s.riding ? "Dismount" : "Ride");
		rideButton.setEnabled(s.riding || s.canRide);
		if (!s.favourites.equals(listedFavourites))
		{
			fillMountList(s.favourites);
		}
		mountBox.setSelectedItem(nameOfChoice(s.chosen));
		favouriteButton.setText(s.favourites.contains(s.chosen) ? "Unfavourite" : "Favourite");
		favouriteButton.setEnabled(s.chosen > 0);
		saddleStyleBox.setSelectedItem(s.saddleStyle);
		saddleBox.setSelected(s.saddle);
		reinsBox.setSelected(s.reins);
		motionBox.setSelected(s.motion);
		everyoneBox.setSelected(s.everyone);
		heldBox.setSelected(s.hideHeld);
		capeBox.setSelected(s.hideCape);
		tweakTitle.setText(s.petName == null ? "Adjustments for your pet" : "Adjustments for " + s.petName);
		poseBox.setSelectedItem(s.tweaks.pose);
		sizeSlider.setValue(s.tweaks.size);
		heightSlider.setValue(s.tweaks.seatHeight);
		forwardSlider.setValue(s.tweaks.seatForward);
		setEnabledDeep(tweakPanel, s.petName != null && s.canRide);
		updating = false;
	}

	/** Fills the Mount list: your pet, then favourites, then every other mount, each alphabetically. */
	private void fillMountList(java.util.Set<Integer> favourites)
	{
		boolean was = updating;
		updating = true;
		listedFavourites = new java.util.HashSet<>(favourites);
		mountCell.favourites.clear();
		mountBox.removeAllItems();
		mountBox.addItem(YOUR_PET);
		for (java.util.Map.Entry<String, Integer> e : MountFits.choices().entrySet())
		{
			if (favourites.contains(e.getValue()))
			{
				mountBox.addItem(e.getKey());
				mountCell.favourites.add(e.getKey());
			}
		}
		for (java.util.Map.Entry<String, Integer> e : MountFits.choices().entrySet())
		{
			if (!favourites.contains(e.getValue()))
			{
				mountBox.addItem(e.getKey());
			}
		}
		updating = was;
	}

	/** One row of the Mount list: the mount's picture and its name, like a card. */
	private static final class MountCell extends JPanel implements ListCellRenderer<String>
	{
		private static final Color GOLD = new Color(255, 200, 40);
		private final MountIcons icons;
		private final JLabel picture = new JLabel();
		private final JLabel name = new JLabel();
		/** Names of starred mounts, shown in gold. */
		final java.util.Set<String> favourites = new java.util.HashSet<>();

		MountCell(MountIcons icons)
		{
			this.icons = icons;
			setLayout(new BorderLayout(8, 0));
			picture.setPreferredSize(new Dimension(MountIcons.WIDTH, MountIcons.HEIGHT));
			picture.setHorizontalAlignment(JLabel.CENTER);
			name.setFont(FontManager.getRunescapeBoldFont());
			name.setForeground(Color.WHITE);
			add(picture, BorderLayout.WEST);
			add(name, BorderLayout.CENTER);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
			boolean selected, boolean focused)
		{
			ImageIcon icon = icons.get(value);
			picture.setIcon(icon);
			picture.setVisible(icon != null);
			name.setText(value);
			name.setForeground(favourites.contains(value) ? GOLD : Color.WHITE);
			setToolTipText(favourites.contains(value) ? value + " (favourite)" : value);
			boolean inList = index >= 0;
			setBackground(inList && selected ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createCompoundBorder(
				inList ? BorderFactory.createCompoundBorder(
					new EmptyBorder(2, 2, 2, 2),
					BorderFactory.createLineBorder(selected ? GOLD : ColorScheme.MEDIUM_GRAY_COLOR, selected ? 2 : 1))
					: new EmptyBorder(0, 0, 0, 0),
				new EmptyBorder(icon != null ? 2 : 8, 4, icon != null ? 2 : 8, 4)));
			return this;
		}
	}

	private static String nameOfChoice(int npcId)
	{
		if (npcId > 0)
		{
			for (java.util.Map.Entry<String, Integer> e : MountFits.choices().entrySet())
			{
				if (e.getValue() == npcId)
				{
					return e.getKey();
				}
			}
		}
		return YOUR_PET;
	}

	private void tweaksChanged()
	{
		if (updating)
		{
			return;
		}
		RiderPose pose = (RiderPose) poseBox.getSelectedItem();
		actions.saveTweaks(new PetTweaks(sizeSlider.getValue(), heightSlider.getValue(), forwardSlider.getValue(), pose));
	}

	private void checkbox(JCheckBox box, String key)
	{
		box.setFocusPainted(false);
		box.addActionListener(e ->
		{
			if (!updating)
			{
				actions.setOption(key, box.isSelected());
			}
		});
	}

	private JSlider slider(int min, int max, int value)
	{
		JSlider s = new JSlider(min, max, value);
		s.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Save when the player lets go, not on every step of the drag.
		s.addChangeListener(e ->
		{
			if (!s.getValueIsAdjusting())
			{
				tweaksChanged();
			}
		});
		return s;
	}

	private JPanel labelled(String text, JSlider s)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel value = new JLabel(String.valueOf(s.getValue()));
		value.setFont(FontManager.getRunescapeSmallFont());
		s.addChangeListener(e -> value.setText(String.valueOf(s.getValue())));
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		head.add(small(text), BorderLayout.WEST);
		head.add(value, BorderLayout.EAST);
		p.add(head, BorderLayout.NORTH);
		p.add(s, BorderLayout.CENTER);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		return p;
	}

	private static JLabel small(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static <T extends Component> T left(T c)
	{
		if (c instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		return c;
	}

	private static void setEnabledDeep(Component c, boolean enabled)
	{
		c.setEnabled(enabled);
		if (c instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) c).getComponents())
			{
				setEnabledDeep(child, enabled);
			}
		}
	}

	/** Convenience for the plugin: run an action with the panel's state. */
	static Consumer<State> onSwing(MountStablePanel panel)
	{
		return s -> javax.swing.SwingUtilities.invokeLater(() -> panel.show(s));
	}
}
