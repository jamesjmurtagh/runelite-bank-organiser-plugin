/*
 * Copyright (c) 2026, james
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * Copyright (c) 2018, Ron Young <https://github.com/raiyni>
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
package com.bankorganiser.banktab;

import com.bankorganiser.BankOrganiserConfig;
import com.bankorganiser.loadout.LoadoutManager;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.bank.BankSearch;

/**
 * The plugin's bank buttons. The "Organise" button flips between the organised category
 * layout and the vanilla bank — unlike Quest Helper's button (off until clicked), ours
 * defaults to ON, so the organised layout replaces the bank's default behaviour and the
 * button is the opt-out. A second "Loadouts" button (only shown when loadouts are
 * configured) toggles the loadout sections at the top of the bank.
 *
 * <p>Neither forces the bank into a fake "searching" state the way Quest Helper does; they
 * only toggle flags that {@link BankLayoutManager} reads. That lets the plugins coexist:
 * when QH's quest tab is active the bank really is searching and the layout manager steps
 * aside. The buttons are positioned clear of QH's so they don't overlap.
 */
@Singleton
public class BankOrganiserButton
{
	private static final String TOGGLE = "Toggle organise";
	private static final String BUTTON_NAME = "bank-organiser";
	private static final String LOADOUT_TOGGLE = "Toggle loadouts";
	private static final String LOADOUT_BUTTON_NAME = "bank-organiser-loadouts";

	private static final int BUTTON_SIZE = 25;
	// Quest Helper anchors its button at x=408; sit clear of it to coexist.
	private static final int BUTTON_X = 380;
	private static final int LOADOUT_BUTTON_X = 352;
	private static final int BUTTON_Y = 5;

	/** Whether the organised layout is enabled. Defaults to on (replaces default view). */
	@Getter
	private boolean organised = true;
	/** Whether the loadout sections are shown. Defaults to on. */
	@Getter
	private boolean showLoadouts = true;

	private Widget parent;
	private Widget backgroundWidget;
	private Widget iconWidget;
	private Widget loadoutBackgroundWidget;
	private Widget loadoutIconWidget;

	private final Client client;
	private final ClientThread clientThread;
	private final BankSearch bankSearch;
	private final BankOrganiserConfig config;
	private final LoadoutManager loadoutManager;

	@Inject
	public BankOrganiserButton(Client client, ClientThread clientThread, BankSearch bankSearch,
		BankOrganiserConfig config, LoadoutManager loadoutManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.bankSearch = bankSearch;
		this.config = config;
		this.loadoutManager = loadoutManager;
	}

	public void init()
	{
		if (isHidden())
		{
			return;
		}

		parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}

		backgroundWidget = createGraphic(BUTTON_NAME, toggleSprite(organised), BUTTON_SIZE, BUTTON_SIZE,
			BUTTON_X, BUTTON_Y);
		backgroundWidget.setAction(1, TOGGLE);
		backgroundWidget.setOnOpListener((JavaScriptCallback) this::handleButton);

		iconWidget = createGraphic("", SpriteID.Mapfunction.BANK, BUTTON_SIZE - 6, BUTTON_SIZE - 6,
			BUTTON_X + 3, BUTTON_Y + 3);

		// Loadout toggle — only worth showing when the player has loadouts configured.
		if (!loadoutManager.getLoadouts().isEmpty())
		{
			loadoutBackgroundWidget = createGraphic(LOADOUT_BUTTON_NAME, toggleSprite(showLoadouts),
				BUTTON_SIZE, BUTTON_SIZE, LOADOUT_BUTTON_X, BUTTON_Y);
			loadoutBackgroundWidget.setAction(1, LOADOUT_TOGGLE);
			loadoutBackgroundWidget.setOnOpListener((JavaScriptCallback) this::handleLoadoutButton);

			loadoutIconWidget = createGraphic("", SpriteID.Mapfunction.PLATEBODY_SHOP,
				BUTTON_SIZE - 6, BUTTON_SIZE - 6, LOADOUT_BUTTON_X + 3, BUTTON_Y + 3);
		}
	}

	public void destroy()
	{
		parent = null;
		for (Widget w : new Widget[]{iconWidget, backgroundWidget, loadoutIconWidget, loadoutBackgroundWidget})
		{
			if (w != null)
			{
				w.setHidden(true);
			}
		}
	}

	public boolean isHidden()
	{
		Widget widget = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return widget == null || widget.isHidden();
	}

	private void handleButton(ScriptEvent event)
	{
		if (event.getOp() != 2)
		{
			return;
		}

		organised = !organised;
		if (backgroundWidget != null)
		{
			backgroundWidget.setSpriteId(toggleSprite(organised));
			backgroundWidget.revalidate();
		}
		onToggle();
	}

	private void handleLoadoutButton(ScriptEvent event)
	{
		if (event.getOp() != 2)
		{
			return;
		}

		showLoadouts = !showLoadouts;
		if (loadoutBackgroundWidget != null)
		{
			loadoutBackgroundWidget.setSpriteId(toggleSprite(showLoadouts));
			loadoutBackgroundWidget.revalidate();
		}
		onToggle();
	}

	private void onToggle()
	{
		if (config.playSound())
		{
			client.playSoundEffect(SoundEffectID.UI_BOOP);
		}
		// Rebuild the bank so the layout manager re-applies the layout.
		bankSearch.reset(true);
	}

	private static int toggleSprite(boolean selected)
	{
		return selected
			? SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
			: SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL;
	}

	private Widget createGraphic(String name, int spriteId, int width, int height, int x, int y)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);

		widget.setSpriteId(spriteId);
		widget.setOnOpListener(ScriptID.NULL);
		widget.setHasListener(true);
		widget.setName(name);
		widget.revalidate();

		return widget;
	}
}
