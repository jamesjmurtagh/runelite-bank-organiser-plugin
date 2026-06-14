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
package com.bankorganiser.banktab;

import com.bankorganiser.category.BankCategoriser;
import com.bankorganiser.loadout.LoadoutManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.plugins.bank.BankSearch;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@link BankLayoutManager} through the exact event sequence the game fires
 * ({@code BANKMAIN_FINISHBUILDING}) against a simulated bank + widget tree, then asserts
 * the rendered layout: that items are grouped into the right sections in {@link Category}
 * order, headers are drawn, items land on the 8-per-row grid at the same coordinates
 * Quest Helper uses, and the scrollbar is sized. Also covers the gating: the layout is
 * suppressed when the button is off, when the bank is searching (which is how it yields
 * to Quest Helper), and when a specific bank tab is open.
 */
public class BankLayoutManagerTest
{
	// Same layout constants as BankLayoutManager / QuestBankTab.
	private static final int ROW_START_X = 51;
	private static final int H_SPACING = 48;
	private static final int V_SPACING = 36;
	private static final int HEADER_HEIGHT = 5 + 15; // LINE_VERTICAL_SPACING + TEXT_HEIGHT

	private Client client;
	private ClientThread clientThread;
	private BankCategoriser categoriser;
	private BankOrganiserButton button;
	private BankSearch bankSearch;
	private LoadoutManager loadoutManager;
	private ChatMessageManager chatMessageManager;
	private BankLayoutManager manager;

	private Widget container;
	private Widget[] itemWidgets;
	private final List<Widget> createdWidgets = new ArrayList<>();

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		categoriser = mock(BankCategoriser.class);
		button = mock(BankOrganiserButton.class);
		bankSearch = mock(BankSearch.class);
		loadoutManager = mock(LoadoutManager.class);
		chatMessageManager = mock(ChatMessageManager.class);
		manager = new BankLayoutManager(client, clientThread, categoriser, button, bankSearch,
			loadoutManager, chatMessageManager);

		// Organised by default unless a test says otherwise.
		lenient().when(button.isOrganised()).thenReturn(true);
		lenient().when(loadoutManager.getLoadouts()).thenReturn(Collections.emptyList());
		// Section draw order (custom sections would precede these; none in these tests).
		lenient().when(categoriser.sectionOrder()).thenReturn(List.of("Weapons", "Food", "Currency"));

		// Run deferred client-thread work synchronously.
		doAnswer(inv -> {
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeAtTickEnd(any(Runnable.class));
		doAnswer(inv -> {
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
	}

	/** A reusable item-slot widget mock; getText() must be non-null for hide logic. */
	private Widget itemWidget()
	{
		Widget w = mock(Widget.class);
		lenient().when(w.getText()).thenReturn("");
		lenient().when(w.getItemId()).thenReturn(-1);
		return w;
	}

	private void setUpBankInterface(int slots)
	{
		itemWidgets = new Widget[slots];
		for (int i = 0; i < slots; i++)
		{
			itemWidgets[i] = itemWidget();
		}

		container = mock(Widget.class);
		when(container.getChildren()).thenReturn(itemWidgets);
		when(container.getDynamicChildren()).thenReturn(itemWidgets);
		for (int i = 0; i < slots; i++)
		{
			when(container.getChild(i)).thenReturn(itemWidgets[i]);
		}
		when(container.getHeight()).thenReturn(100);
		when(container.getScrollY()).thenReturn(0);

		// Header line / text widgets are created on demand; collect them so we can read
		// back their setText() calls after the run.
		when(container.createChild(anyInt(), anyInt())).thenAnswer(inv -> {
			Widget w = mock(Widget.class);
			createdWidgets.add(w);
			return w;
		});

		when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(container);
	}

	private ItemComposition composition(String name)
	{
		ItemComposition comp = mock(ItemComposition.class);
		lenient().when(comp.getName()).thenReturn(name);
		lenient().when(comp.getPlaceholderTemplateId()).thenReturn(-1);
		lenient().when(comp.getPlaceholderId()).thenReturn(-1);
		lenient().when(comp.getIntValue(anyInt())).thenReturn(-1);
		return comp;
	}

	private void registerItem(int id, String section, String name)
	{
		when(categoriser.sectionFor(id)).thenReturn(section);
		ItemComposition comp = composition(name); // build fully before stubbing below
		when(client.getItemDefinition(id)).thenReturn(comp);
	}

	/** The section header strings, in the order their widgets were created. */
	private List<String> headerTexts()
	{
		List<String> texts = new ArrayList<>();
		for (Widget w : createdWidgets)
		{
			for (var inv : org.mockito.Mockito.mockingDetails(w).getInvocations())
			{
				if (inv.getMethod().getName().equals("setText"))
				{
					texts.add(inv.getArgument(0));
				}
			}
		}
		return texts;
	}

	private ScriptPostFired script(int id)
	{
		ScriptPostFired e = mock(ScriptPostFired.class);
		when(e.getScriptId()).thenReturn(id);
		return e;
	}

	private void setBankSearching(boolean searching)
	{
		int[] stack = {searching ? 1 : 0};
		when(client.getIntStack()).thenReturn(stack);
		when(client.getIntStackSize()).thenReturn(1);
		manager.onScriptPostFired(script(ScriptID.BANKMAIN_SEARCHING));
	}

	@Test
	public void laysOutSectionsInCategoryOrderOnTheGrid()
	{
		setUpBankInterface(20);

		// 9 weapons (forces a row wrap), 2 food, 1 currency — supplied to the bank in a
		// deliberately mixed order, with a duplicate and a filler to prove they're handled.
		int[] weapons = {100, 101, 102, 103, 104, 105, 106, 107, 108};
		for (int w : weapons)
		{
			registerItem(w, "Weapons", "Weapon " + w);
		}
		registerItem(200, "Food", "Lobster");
		registerItem(201, "Food", "Shark");
		registerItem(995, "Currency", "Coins");

		List<Item> bankItems = new ArrayList<>();
		bankItems.add(new Item(100, 1));
		bankItems.add(new Item(200, 1));
		bankItems.add(new Item(101, 1));
		bankItems.add(new Item(995, 5000));
		bankItems.add(new Item(100, 1));                 // duplicate -> ignored
		bankItems.add(new Item(ItemID.BANK_FILLER, 1));  // filler -> ignored
		for (int i = 2; i < weapons.length; i++)
		{
			bankItems.add(new Item(weapons[i], 1));      // 102..108
		}
		bankItems.add(new Item(201, 1));

		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(bankItems.toArray(new Item[0]));
		lenient().when(bank.count(anyInt())).thenReturn(1);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.BANK)).thenReturn(bank);

		manager.onScriptPostFired(script(ScriptID.BANKMAIN_FINISHBUILDING));

		// Sections appear in Category declaration order: Weapons, Food, Currency.
		assertEquals(List.of("Weapons", "Food", "Currency"), headerTexts());

		// Weapons grid: ids 100..108 fill widgets 0..8.
		verify(itemWidgets[0]).setItemId(100);
		verify(itemWidgets[0]).setOriginalX(ROW_START_X);
		verify(itemWidgets[0]).setOriginalY(HEADER_HEIGHT);              // y = 20

		verify(itemWidgets[7]).setItemId(107);
		verify(itemWidgets[7]).setOriginalX(ROW_START_X + 7 * H_SPACING); // x = 387
		verify(itemWidgets[7]).setOriginalY(HEADER_HEIGHT);              // y = 20

		verify(itemWidgets[8]).setItemId(108);
		verify(itemWidgets[8]).setOriginalX(ROW_START_X);               // wrapped to col 0
		verify(itemWidgets[8]).setOriginalY(HEADER_HEIGHT + V_SPACING);  // y = 56

		// Food section starts below the weapons section (2 rows) + its own header.
		int foodTop = HEADER_HEIGHT + 2 * V_SPACING + HEADER_HEIGHT;     // 112
		verify(itemWidgets[9]).setItemId(200);
		verify(itemWidgets[9]).setOriginalX(ROW_START_X);
		verify(itemWidgets[9]).setOriginalY(foodTop);
		verify(itemWidgets[10]).setItemId(201);
		verify(itemWidgets[10]).setOriginalX(ROW_START_X + H_SPACING);   // col 1
		verify(itemWidgets[10]).setOriginalY(foodTop);

		// Currency section below food (1 row) + its own header.
		int currencyTop = foodTop + V_SPACING + HEADER_HEIGHT;           // 168
		verify(itemWidgets[11]).setItemId(995);
		verify(itemWidgets[11]).setOriginalX(ROW_START_X);
		verify(itemWidgets[11]).setOriginalY(currencyTop);

		// Total content height = currencyTop + 1 row = 204; exceeds the 100px viewport.
		int totalHeight = currencyTop + V_SPACING;                      // 204
		verify(container).setScrollHeight(totalHeight);
		verify(client).runScript(ScriptID.UPDATE_SCROLLBAR,
			InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, 0);

		verify(itemWidgets[0]).setHidden(false);
		verify(itemWidgets[11]).setHidden(false);
	}

	@Test
	public void alphabetisesItemsWithinASection()
	{
		setUpBankInterface(10);
		// Supplied to the bank out of alphabetical order.
		registerItem(200, "Food", "Shark");
		registerItem(201, "Food", "Anchovies");
		registerItem(202, "Food", "Lobster");

		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(new Item[]{new Item(200, 1), new Item(201, 1), new Item(202, 1)});
		lenient().when(bank.count(anyInt())).thenReturn(1);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.BANK)).thenReturn(bank);

		manager.onScriptPostFired(script(ScriptID.BANKMAIN_FINISHBUILDING));

		// Placed alphabetically: Anchovies, Lobster, Shark.
		verify(itemWidgets[0]).setItemId(201);
		verify(itemWidgets[1]).setItemId(202);
		verify(itemWidgets[2]).setItemId(200);
	}

	@Test
	public void doesNothingWhenButtonOff()
	{
		setUpBankInterface(5);
		when(button.isOrganised()).thenReturn(false);

		manager.onScriptPostFired(script(ScriptID.BANKMAIN_FINISHBUILDING));

		assertEquals(List.of(), headerTexts());
		verify(container, never()).setScrollHeight(anyInt());
	}

	@Test
	public void doesNothingWhileSearching()
	{
		// QH's active quest tab forces the bank into a searching state; this is how we
		// yield to it (and to ordinary bank searches).
		setUpBankInterface(5);
		setBankSearching(true);

		manager.onScriptPostFired(script(ScriptID.BANKMAIN_FINISHBUILDING));

		assertEquals(List.of(), headerTexts());
		verify(container, never()).setScrollHeight(anyInt());
	}

	@Test
	public void doesNothingOnASpecificBankTab()
	{
		setUpBankInterface(5);
		when(client.getVarbitValue(VarbitID.BANK_CURRENTTAB)).thenReturn(3); // not the all-items tab

		manager.onScriptPostFired(script(ScriptID.BANKMAIN_FINISHBUILDING));

		assertEquals(List.of(), headerTexts());
		verify(container, never()).setScrollHeight(anyInt());
	}

	@Test
	public void resumesOrganisingAfterSearchEnds()
	{
		setUpBankInterface(5);
		registerItem(995, "Currency", "Coins");
		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(new Item[]{new Item(995, 1)});
		lenient().when(bank.count(anyInt())).thenReturn(1);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.BANK)).thenReturn(bank);

		setBankSearching(true);
		manager.onScriptPostFired(script(ScriptID.BANKMAIN_FINISHBUILDING));
		verify(container, never()).setScrollHeight(anyInt());

		// Search ends -> next build organises again.
		setBankSearching(false);
		manager.onScriptPostFired(script(ScriptID.BANKMAIN_FINISHBUILDING));
		assertEquals(List.of("Currency"), headerTexts());
	}
}
