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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Import/export of the plugin's configuration as a single base64 string. The two
 * boolean config items act as buttons: flipping "Export to clipboard" copies the current
 * settings to the clipboard; flipping "Load imported config" applies the pasted string.
 * Both flip themselves back off.
 */
@Slf4j
@Singleton
public class ConfigPortability
{
	private static final int VERSION = 1;

	private final ConfigManager configManager;
	private final BankOrganiserConfig config;
	private final ChatMessageManager chatMessageManager;
	private final Gson gson;

	@Inject
	public ConfigPortability(ConfigManager configManager, BankOrganiserConfig config,
		ChatMessageManager chatMessageManager, Gson gson)
	{
		this.configManager = configManager;
		this.config = config;
		this.chatMessageManager = chatMessageManager;
		this.gson = gson;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BankOrganiserConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("exportToClipboard".equals(event.getKey()) && config.exportToClipboard())
		{
			configManager.setConfiguration(BankOrganiserConfig.GROUP, "exportToClipboard", false);
			copyToClipboard(export());
			message("Configuration copied to the clipboard.");
		}
		else if ("loadImportedConfig".equals(event.getKey()) && config.loadImportedConfig())
		{
			configManager.setConfiguration(BankOrganiserConfig.GROUP, "loadImportedConfig", false);
			if (applyImport(config.importConfig()))
			{
				configManager.setConfiguration(BankOrganiserConfig.GROUP, "importConfig", "");
				message("Configuration imported.");
			}
			else
			{
				message("Import failed: that doesn't look like a valid configuration string.");
			}
		}
	}

	/** Serialises the current settings to a base64 string. */
	public String export()
	{
		Export data = new Export();
		data.version = VERSION;
		data.playSound = config.playSound();
		data.categoryOverrides = config.categoryOverrides();
		data.loadouts = config.loadouts();
		data.categoryOrder = config.categoryOrder();
		return Base64.getEncoder().encodeToString(gson.toJson(data).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Decodes a base64 string and writes its settings into the config. Returns false (and
	 * changes nothing) if the string can't be parsed.
	 */
	public boolean applyImport(String encoded)
	{
		if (encoded == null || encoded.trim().isEmpty())
		{
			return false;
		}

		final Export data;
		try
		{
			byte[] json = Base64.getDecoder().decode(encoded.trim());
			data = gson.fromJson(new String(json, StandardCharsets.UTF_8), Export.class);
		}
		catch (IllegalArgumentException | JsonSyntaxException ex)
		{
			log.debug("Invalid config import string", ex);
			return false;
		}

		if (data == null)
		{
			return false;
		}

		// Only apply fields that were present, so partial / future strings degrade gracefully.
		if (data.playSound != null)
		{
			configManager.setConfiguration(BankOrganiserConfig.GROUP, "playSound", data.playSound);
		}
		setIfPresent("categoryOverrides", data.categoryOverrides);
		setIfPresent("loadouts", data.loadouts);
		setIfPresent("categoryOrder", data.categoryOrder);
		return true;
	}

	private void setIfPresent(String key, String value)
	{
		if (value != null)
		{
			configManager.setConfiguration(BankOrganiserConfig.GROUP, key, value);
		}
	}

	private void copyToClipboard(String text)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		}
		catch (RuntimeException ex)
		{
			log.warn("Could not access the clipboard", ex);
		}
	}

	private void message(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder().append("[Bank Assistant] " + text).build())
			.build());
	}

	/** The serialised shape. Boxed types so missing fields are null, not defaults. */
	private static final class Export
	{
		private int version;
		private Boolean playSound;
		private String categoryOverrides;
		private String loadouts;
		private String categoryOrder;
	}
}
