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

import com.bankorganiser.BankOrganiserConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

/**
 * Parses and caches the user's loadouts, resolving item names to ids via an index built
 * from the game's item definitions. All methods must be called on the client thread (the
 * item-name index is built from the live cache).
 */
@Slf4j
@Singleton
public class LoadoutManager
{
	private final Client client;
	private final ItemManager itemManager;
	private final BankOrganiserConfig config;

	private Map<String, Integer> nameIndex;
	private String cachedRaw;
	private List<Loadout> cached = Collections.emptyList();

	@Inject
	public LoadoutManager(Client client, ItemManager itemManager, BankOrganiserConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
	}

	/** The parsed loadouts, re-parsing only when the config text changes. */
	public List<Loadout> getLoadouts()
	{
		final String raw = config.loadouts();
		if (raw == null || raw.trim().isEmpty())
		{
			cachedRaw = raw;
			cached = Collections.emptyList();
			return cached;
		}
		if (raw.equals(cachedRaw))
		{
			return cached;
		}
		cachedRaw = raw;
		ensureIndex();
		cached = LoadoutParser.parse(raw, this::resolveName);
		return cached;
	}

	private int resolveName(String lowerName)
	{
		Integer id = nameIndex.get(lowerName);
		return id == null ? -1 : id;
	}

	private void ensureIndex()
	{
		if (nameIndex != null)
		{
			return;
		}
		Map<String, Integer> index = new HashMap<>();
		final int count = client.getItemCount();
		for (int id = 0; id < count; id++)
		{
			final ItemComposition comp = itemManager.getItemComposition(id);
			if (comp == null)
			{
				continue;
			}
			final String name = comp.getName();
			if (name == null || name.isEmpty() || name.equalsIgnoreCase("null"))
			{
				continue;
			}
			// Only index base items so noted/placeholder variants don't shadow them.
			if (itemManager.canonicalize(id) != id)
			{
				continue;
			}
			index.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
		}
		nameIndex = index;
	}
}
