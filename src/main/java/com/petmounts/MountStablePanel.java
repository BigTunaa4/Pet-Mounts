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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
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

		State(String petName, String status, boolean canRide, boolean riding, PetTweaks tweaks,
			boolean saddle, boolean reins, boolean motion, boolean everyone, boolean hideHeld, boolean hideCape, int chosen)
		{
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
				&& hideCape == o.hideCape && chosen == o.chosen && tweaks.size == o.tweaks.size && tweaks.seatHeight == o.tweaks.seatHeight
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

		// Which pet to ride: your follower, or any rideable pet, even one you haven't got yet.
		content.add(left(small("Mount")));
		mountBox.addItem(YOUR_PET);
		for (String name : MountFits.choices().keySet())
		{
			mountBox.addItem(name);
		}
		mountBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		mountBox.setToolTipText("Ride the pet following you, or pick any pet to ride, even one you don't have yet");
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
		show(new State(null, "Summon one of your pets to ride it.", false, false, PetTweaks.NONE, true, true, true, true, true, true, 0));
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
		mountBox.setSelectedItem(nameOfChoice(s.chosen));
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
