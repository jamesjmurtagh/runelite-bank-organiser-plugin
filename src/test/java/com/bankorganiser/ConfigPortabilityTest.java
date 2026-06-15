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

import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigPortabilityTest
{
	private static final String GROUP = BankOrganiserConfig.GROUP;

	@Mock
	private ConfigManager configManager;
	@Mock
	private BankOrganiserConfig config;
	@Mock
	private ChatMessageManager chatMessageManager;

	private ConfigPortability portability;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		portability = new ConfigPortability(configManager, config, chatMessageManager, new com.google.gson.Gson());
	}

	@Test
	public void exportThenImportRoundTripsEveryField()
	{
		when(config.playSound()).thenReturn(true);
		when(config.categoryOverrides()).thenReturn("pickaxe=Mining\n/\\w* rune/=Runes");
		when(config.loadouts()).thenReturn("[Herb Run]\nEctophial\n4 Law rune");
		when(config.categoryOrder()).thenReturn("Food\nPotions");

		String encoded = portability.export();
		assertTrue(portability.applyImport(encoded));

		verify(configManager).setConfiguration(GROUP, "playSound", true);
		verify(configManager).setConfiguration(GROUP, "categoryOverrides", "pickaxe=Mining\n/\\w* rune/=Runes");
		verify(configManager).setConfiguration(GROUP, "loadouts", "[Herb Run]\nEctophial\n4 Law rune");
		verify(configManager).setConfiguration(GROUP, "categoryOrder", "Food\nPotions");
	}

	@Test
	public void invalidStringsAreRejectedWithoutChangingConfig()
	{
		lenient().when(config.playSound()).thenReturn(false);

		assertFalse(portability.applyImport(null));
		assertFalse(portability.applyImport(""));
		assertFalse(portability.applyImport("   "));
		assertFalse(portability.applyImport("this is not base64 %%%"));

		verify(configManager, never()).setConfiguration(org.mockito.ArgumentMatchers.eq(GROUP),
			org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void exportProducesDecodableBase64()
	{
		when(config.playSound()).thenReturn(false);
		when(config.categoryOverrides()).thenReturn("");
		when(config.loadouts()).thenReturn("");
		when(config.categoryOrder()).thenReturn("");

		String encoded = portability.export();
		// Round-trips through Base64 without throwing.
		String json = new String(java.util.Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(json.contains("\"version\""));
	}
}
