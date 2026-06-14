/*
 * Copyright (c) 2026, james
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DAMAGES ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE.
 */
package com.bankorganiser;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BankOrganiserConfig.GROUP)
public interface BankOrganiserConfig extends Config
{
	String GROUP = "bankorganiser";

	@ConfigItem(
		keyName = "playSound",
		name = "Play toggle sound",
		description = "Play a click sound when toggling the organised view.",
		position = 1
	)
	default boolean playSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "categoryOverrides",
		name = "Category overrides",
		description = "Force items into a category of your choice. One rule per line, as"
			+ " match=Category.\n"
			+ "'match' is part of an item name (case-insensitive), an exact item id, or a"
			+ " /regex/ pattern (wrapped in slashes, case-insensitive).\n"
			+ "A category name that isn't a built-in one creates a new section (shown at the"
			+ " top). Earlier lines win.\n"
			+ "Example:\n"
			+ "pickaxe=Mining\n"
			+ "/\\w* rune/=Runes\n"
			+ "995=Currency",
		position = 2
	)
	default String categoryOverrides()
	{
		return "";
	}

	@ConfigItem(
		keyName = "loadouts",
		name = "Loadouts",
		description = "Custom loadout sections shown at the top of the bank, with the exact"
			+ " items + quantities you need as placeholders (greyed if you're missing them),"
			+ " like Quest Helper. These do NOT remove items from the normal categories.\n"
			+ "Start each loadout with [Name], then one item per line as: optional quantity,"
			+ " then an item name (or id:1234). Blank/# lines are ignored.\n"
			+ "Example:\n"
			+ "[Herb Farming Run]\n"
			+ "Ectophial\n"
			+ "4 Law rune\n"
			+ "14 Air rune\n"
			+ "1 Earth rune\n"
			+ "1 Water rune\n"
			+ "Dramen staff",
		position = 3
	)
	default String loadouts()
	{
		return "";
	}
}
