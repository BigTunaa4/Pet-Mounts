package com.petmounts;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class ConfigTypesTest
{
	/**
	 * RuneLite reads each setting through a generated proxy class, which can only use public types. A setting
	 * whose type isn't public throws on every read, every frame, and freezes the game.
	 */
	@Test
	public void everySettingTypeIsPublic()
	{
		for (Method m : PetMountsConfig.class.getDeclaredMethods())
		{
			Class<?> type = m.getReturnType();
			if (type.isPrimitive() || type.getName().startsWith("java."))
			{
				continue;
			}
			assertTrue("Setting " + m.getName() + " uses " + type.getSimpleName() + ", which must be public",
				Modifier.isPublic(type.getModifiers()));
		}
	}
}
