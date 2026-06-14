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
package com.bankorganiser.loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the loadout config into {@link Loadout}s. The format is:
 * <pre>
 * [Loadout name]
 * &lt;optional qty&gt; &lt;item name, or id:1234&gt;
 * ...
 * </pre>
 * Blank lines and lines starting with {@code #} are ignored. Item lines before any
 * {@code [Name]} header, and items whose name can't be resolved, are dropped.
 */
public final class LoadoutParser
{
	private static final Pattern HEADER = Pattern.compile("^\\[(.+)]$");
	private static final Pattern QTY_PREFIX = Pattern.compile("^(\\d+)\\s+(.+)$");
	private static final int MAX_QTY = 2_000_000_000;

	private LoadoutParser()
	{
	}

	/**
	 * @param raw         the raw config text
	 * @param resolveName given a lower-cased item name, returns its item id, or -1 if unknown
	 */
	public static List<Loadout> parse(String raw, ToIntFunction<String> resolveName)
	{
		List<Loadout> loadouts = new ArrayList<>();
		if (raw == null)
		{
			return loadouts;
		}

		Loadout current = null;
		for (String line : raw.split("\\R"))
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#"))
			{
				continue;
			}

			Matcher header = HEADER.matcher(trimmed);
			if (header.matches())
			{
				String name = header.group(1).trim();
				current = name.isEmpty() ? null : new Loadout(name);
				if (current != null)
				{
					loadouts.add(current);
				}
				continue;
			}

			if (current == null)
			{
				continue;
			}

			int qty = 1;
			String namePart = trimmed;
			Matcher qtyMatch = QTY_PREFIX.matcher(trimmed);
			if (qtyMatch.matches())
			{
				qty = clampQty(qtyMatch.group(1));
				namePart = qtyMatch.group(2).trim();
			}

			int itemId = resolveItemId(namePart, resolveName);
			if (itemId > 0)
			{
				current.getItems().add(new LoadoutItem(itemId, qty, namePart));
			}
		}

		loadouts.removeIf(Loadout::isEmpty);
		return loadouts;
	}

	private static int resolveItemId(String namePart, ToIntFunction<String> resolveName)
	{
		String lower = namePart.toLowerCase(Locale.ROOT);
		if (lower.startsWith("id:"))
		{
			try
			{
				return Integer.parseInt(lower.substring(3).trim());
			}
			catch (NumberFormatException ex)
			{
				return -1;
			}
		}
		return resolveName.applyAsInt(lower);
	}

	private static int clampQty(String digits)
	{
		try
		{
			long q = Long.parseLong(digits);
			return (int) Math.max(1, Math.min(MAX_QTY, q));
		}
		catch (NumberFormatException ex)
		{
			return 1;
		}
	}
}
