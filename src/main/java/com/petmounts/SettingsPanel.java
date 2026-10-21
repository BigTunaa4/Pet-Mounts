package com.petmounts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;

/**
 * Every Pet Mounts setting, in the Mount Stable: the same settings, sections, order, limits and descriptions as the
 * plugin's page in RuneLite's configuration, built from the plugin's own config so the two never drift apart. A
 * change in either place shows in both.
 */
@Slf4j
final class SettingsPanel extends JPanel
{
	private final ConfigManager configManager;
	private final PetMountsConfig config;
	private final ColorPickerManager colorPickerManager;
	private final String group;
	/** The config's getter for each setting, to read its current value (including the default). */
	private final Map<String, Method> getters = new HashMap<>();
	/** Puts each setting's control back to the saved value. */
	private final Map<String, Runnable> refreshers = new LinkedHashMap<>();
	private boolean updating;

	SettingsPanel(ConfigManager configManager, PetMountsConfig config, ColorPickerManager colorPickerManager)
	{
		this.configManager = configManager;
		this.config = config;
		this.colorPickerManager = colorPickerManager;
		this.group = PetMountsConfig.GROUP;
		for (Method m : PetMountsConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item != null && m.getParameterCount() == 0)
			{
				getters.put(item.keyName(), m);
			}
		}

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);

		ConfigDescriptor cd = configManager.getConfigDescriptor(config);
		// Settings outside any section come first, as on the configuration page.
		Map<String, List<ConfigItemDescriptor>> bySection = new LinkedHashMap<>();
		for (ConfigItemDescriptor item : cd.getItems())
		{
			if (item.getItem().hidden())
			{
				continue;
			}
			bySection.computeIfAbsent(item.getItem().section(), k -> new ArrayList<>()).add(item);
		}
		bySection.values().forEach(l -> l.sort(Comparator.comparingInt(ConfigItemDescriptor::position)));

		List<ConfigItemDescriptor> general = bySection.getOrDefault("", new ArrayList<>());
		if (!general.isEmpty())
		{
			add(section("General", "Hotkey, menus and how you ride", false, general));
		}
		List<ConfigSectionDescriptor> sections = new ArrayList<>(cd.getSections());
		sections.sort(Comparator.comparingInt(ConfigSectionDescriptor::position));
		for (ConfigSectionDescriptor s : sections)
		{
			List<ConfigItemDescriptor> items = bySection.get(s.getKey());
			if (items != null && !items.isEmpty())
			{
				ConfigSection cs = s.getSection();
				add(Box.createRigidArea(new Dimension(0, 4)));
				add(section(cs.name(), cs.description(), cs.closedByDefault(), items));
			}
		}
		refresh();
	}

	/** Shows the saved value of every setting (after a change here, on the configuration page or a profile switch). */
	void refresh()
	{
		updating = true;
		try
		{
			refreshers.values().forEach(Runnable::run);
		}
		finally
		{
			updating = false;
		}
	}

	// ------------------------------------------------------------------
	// Sections
	// ------------------------------------------------------------------

	private JPanel section(String name, String description, boolean closed, List<ConfigItemDescriptor> items)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setOpaque(false);
		left(wrap);

		JLabel header = new JLabel();
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setToolTipText("<html>" + description + "<br>Click to show or hide</html>");
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setBorder(new EmptyBorder(4, 0, 4, 0));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(new EmptyBorder(6, 8, 6, 8));
		left(body);
		for (ConfigItemDescriptor item : items)
		{
			JComponent row = row(item);
			if (row != null)
			{
				body.add(left(row));
				body.add(Box.createRigidArea(new Dimension(0, 4)));
			}
		}

		Consumer<Boolean> setOpen = open ->
		{
			body.setVisible(open);
			header.setText((open ? "▾ " : "▸ ") + name);
			revalidate();
		};
		setOpen.accept(!closed);
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setOpen.accept(!body.isVisible());
			}
		});
		wrap.add(left(header));
		wrap.add(body);
		return wrap;
	}

	// ------------------------------------------------------------------
	// One control per setting, by type
	// ------------------------------------------------------------------

	private JComponent row(ConfigItemDescriptor d)
	{
		ConfigItem item = d.getItem();
		String key = item.keyName();
		String tip = item.description().isEmpty() ? null : "<html><div style='width:180px'>" + item.description() + "</div></html>";
		Class<?> type = d.getType() instanceof Class ? (Class<?>) d.getType() : null;
		if (type == null)
		{
			return null;
		}

		if (type == boolean.class || type == Boolean.class)
		{
			JCheckBox box = new JCheckBox(item.name());
			box.setToolTipText(tip);
			box.setFocusPainted(false);
			box.setOpaque(false);
			box.addActionListener(e -> save(key, box.isSelected()));
			refreshers.put(key, () -> box.setSelected(Boolean.TRUE.equals(get(key))));
			return box;
		}

		if (type == int.class || type == Integer.class)
		{
			Range range = d.getRange();
			int min = range != null ? range.min() : 0;
			int max = range != null ? range.max() : Integer.MAX_VALUE;
			SpinnerNumberModel model = new SpinnerNumberModel(min, min, max, 1);
			JSpinner spinner = new JSpinner(model);
			spinner.setToolTipText(tip);
			Units units = d.getUnits();
			if (units != null)
			{
				((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setToolTipText(units.value());
			}
			spinner.addChangeListener(e -> save(key, spinner.getValue()));
			refreshers.put(key, () ->
			{
				Object v = get(key);
				int value = v instanceof Number ? ((Number) v).intValue() : min;
				spinner.setValue(Math.max(min, Math.min(max, value)));
			});
			String label = units != null ? item.name() + " (" + units.value().trim() + ")" : item.name();
			return labelledRow(label, tip, spinner, 70);
		}

		if (type.isEnum())
		{
			JComboBox<Object> combo = new JComboBox<>(type.getEnumConstants());
			combo.setToolTipText(tip);
			combo.addActionListener(e ->
			{
				if (combo.getSelectedItem() != null)
				{
					save(key, combo.getSelectedItem());
				}
			});
			refreshers.put(key, () -> combo.setSelectedItem(get(key)));
			return stacked(item.name(), tip, combo);
		}

		if (type == Color.class)
		{
			ColorJButton button = new ColorJButton("", Color.WHITE);
			button.setToolTipText(tip);
			button.setFocusPainted(false);
			button.addActionListener(e ->
			{
				Object v = get(key);
				Color current = v instanceof Color ? (Color) v : Color.WHITE;
				RuneliteColorPicker picker = colorPickerManager.create(this, current, item.name(), true);
				picker.setOnColorChange(c ->
				{
					button.setColor(c);
					button.setText(hex(c));
				});
				picker.setOnClose(c -> save(key, c));
				picker.setVisible(true);
			});
			refreshers.put(key, () ->
			{
				Object v = get(key);
				Color c = v instanceof Color ? (Color) v : Color.WHITE;
				button.setColor(c);
				button.setText(hex(c));
			});
			return labelledRow(item.name(), tip, button, 80);
		}

		if (type == Keybind.class)
		{
			JButton button = new JButton();
			button.setToolTipText("<html>Click, then press the key (with Ctrl, Alt or Shift if you like)."
				+ " Backspace or Escape clears it.<br>" + item.description() + "</html>");
			button.setFocusPainted(false);
			Runnable show = () ->
			{
				Object v = get(key);
				button.setText(v instanceof Keybind ? v.toString() : Keybind.NOT_SET.toString());
			};
			button.addActionListener(e -> button.setText("Press a key..."));
			button.addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					int code = e.getKeyCode();
					if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
						|| code == KeyEvent.VK_META)
					{
						return; // wait for the actual key
					}
					Keybind k = code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_ESCAPE
						? Keybind.NOT_SET : new Keybind(code, e.getModifiersEx());
					e.consume();
					save(key, k);
					button.setText(k.toString());
				}
			});
			button.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					show.run();
				}
			});
			refreshers.put(key, show);
			return stacked(item.name(), null, button);
		}

		if (type == String.class)
		{
			JTextField field = new JTextField();
			field.setToolTipText(tip);
			Runnable commit = () ->
			{
				Object v = get(key);
				if (!field.getText().equals(v == null ? "" : v.toString()))
				{
					save(key, field.getText());
				}
			};
			field.addActionListener(e -> commit.run());
			field.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					commit.run();
				}
			});
			refreshers.put(key, () ->
			{
				if (!field.isFocusOwner())
				{
					Object v = get(key);
					field.setText(v == null ? "" : v.toString());
				}
			});
			return stacked(item.name(), tip, field);
		}

		log.debug("No control for setting {} of type {}", key, type);
		return null;
	}

	// ------------------------------------------------------------------
	// Reading and saving
	// ------------------------------------------------------------------

	/** The setting's current value, as the plugin sees it (its default if never changed). */
	private Object get(String key)
	{
		Method m = getters.get(key);
		if (m == null)
		{
			return null;
		}
		try
		{
			return m.invoke(config);
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.debug("Couldn't read setting {}", key, e);
			return null;
		}
	}

	private void save(String key, Object value)
	{
		if (updating)
		{
			return;
		}
		configManager.setConfiguration(group, key, value);
	}

	// ------------------------------------------------------------------
	// Layout helpers
	// ------------------------------------------------------------------

	private static JPanel labelledRow(String name, String tip, JComponent control, int controlWidth)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		JLabel label = small(name);
		label.setToolTipText(tip);
		p.add(label, BorderLayout.CENTER);
		control.setPreferredSize(new Dimension(controlWidth, 24));
		p.add(control, BorderLayout.EAST);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		return p;
	}

	private static JPanel stacked(String name, String tip, JComponent control)
	{
		JPanel p = new JPanel(new BorderLayout(0, 2));
		p.setOpaque(false);
		JLabel label = small(name);
		label.setToolTipText(tip);
		p.add(label, BorderLayout.NORTH);
		p.add(control, BorderLayout.CENTER);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
		return p;
	}

	private static JLabel small(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Color.WHITE);
		return l;
	}

	private static String hex(Color c)
	{
		return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
	}

	private static <T extends JComponent> T left(T c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		return c;
	}
}
