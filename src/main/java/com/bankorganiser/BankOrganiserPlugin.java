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

import com.bankorganiser.banktab.BankLayoutManager;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Bank Assistant",
	description = "Re-lays-out your bank into skill and combat-style categories, with custom "
		+ "overrides and Quest-Helper-style loadout checklists.",
	tags = {"bank", "organise", "organize", "sort", "category", "loadout", "assistant"}
)
public class BankOrganiserPlugin extends Plugin
{
	@Inject
	private EventBus eventBus;

	@Inject
	private BankLayoutManager layoutManager;

	@Inject
	private ConfigPortability configPortability;

	@Override
	protected void startUp()
	{
		layoutManager.startUp(eventBus);
		eventBus.register(configPortability);
	}

	@Override
	protected void shutDown()
	{
		layoutManager.shutDown(eventBus);
		eventBus.unregister(configPortability);
	}

	@Provides
	BankOrganiserConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankOrganiserConfig.class);
	}
}
