/*
 * Copyright (c) 2026, james
 * Copyright (c) 2021, geheur <https://github.com/geheur>
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Ron Young <https://github.com/raiyni>
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
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
import com.bankorganiser.category.BankCategoriser;
import com.bankorganiser.loadout.Loadout;
import com.bankorganiser.loadout.LoadoutItem;
import com.bankorganiser.loadout.LoadoutManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.ParamID;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

import static net.runelite.client.plugins.banktags.BankTagsPlugin.BANK_ITEM_HEIGHT;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.BANK_ITEM_WIDTH;

/**
 * Replaces the bank's default all-items view with a category-organised layout. This is
 * the standalone-plugin analogue of Quest Helper's {@code QuestBankTab}, but instead of
 * being a tab you opt into, it is the bank's default behaviour (toggleable via
 * {@link BankOrganiserButton}).
 *
 * <p>It only reorganises the main "all items" view ({@code BANK_CURRENTTAB == 0}) and
 * steps aside whenever the bank is searching — which includes Quest Helper's quest tab,
 * since QH forces the bank into a searching state when active. That is how the two
 * coexist: QH's button still shows the quest view; otherwise you see categories.
 */
@Slf4j
@Singleton
public class BankLayoutManager
{
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_VERTICAL_SPACING = 36;
	private static final int ITEM_HORIZONTAL_SPACING = 48;
	private static final int ITEM_ROW_START = 51;
	private static final int LINE_VERTICAL_SPACING = 5;
	private static final int LINE_HEIGHT = 2;
	private static final int TEXT_HEIGHT = 15;
	private static final int MAIN_TAB = 0;

	private static final int SECTION_HEADER_COLOR = new Color(228, 216, 162).getRGB();
	private static final int OVERLAY_TEXT_COLOR = Color.WHITE.getRGB();
	private static final int TICK_SPRITE = SpriteID.Checkbox.CHECKED;
	private static final int CROSS_SPRITE = SpriteID.Checkbox.CROSSED;

	private final ArrayList<Widget> addedWidgets = new ArrayList<>();
	/** Loadout placeholder widgets we created, mapped to their entry (for "Details"). */
	private final Map<Widget, LoadoutItem> loadoutWidgets = new HashMap<>();
	private int originalContainerChildren = -1;
	private int currentWidgetToUse = 0;

	/** Whether the bank is in a searching state (real search, tag-tab search, or QH). */
	private boolean bankSearching = false;
	/** Whether our layout was applied on the most recent build (gates the withdraw remap). */
	private boolean layoutApplied = false;

	private final Client client;
	private final ClientThread clientThread;
	private final BankCategoriser categoriser;
	private final BankOrganiserButton button;
	private final BankSearch bankSearch;
	private final LoadoutManager loadoutManager;
	private final ChatMessageManager chatMessageManager;

	@Inject
	public BankLayoutManager(Client client, ClientThread clientThread, BankCategoriser categoriser,
		BankOrganiserButton button, BankSearch bankSearch, LoadoutManager loadoutManager,
		ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.categoriser = categoriser;
		this.button = button;
		this.bankSearch = bankSearch;
		this.loadoutManager = loadoutManager;
		this.chatMessageManager = chatMessageManager;
	}

	public void startUp(EventBus eventBus)
	{
		eventBus.register(this);
		clientThread.invokeLater(button::init);
	}

	public void shutDown(EventBus eventBus)
	{
		eventBus.unregister(this);
		clientThread.invokeLater(() -> {
			button.destroy();
			removeAddedWidgets();
			// Restore the vanilla layout if the bank is open.
			if (!button.isHidden())
			{
				bankSearch.reset(true);
			}
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			button.init();
			// Prime the loadout item-name index now (on the client thread) so the heavy
			// one-time build doesn't happen mid-layout, where it delays item rendering.
			loadoutManager.getLoadouts();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BankOrganiserConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		// Config changes arrive on the AWT thread, but bank/widget access must happen on the
		// client thread — so re-apply the layout (if the bank is open) from there.
		clientThread.invokeLater(() -> {
			if (!button.isHidden())
			{
				bankSearch.reset(true);
			}
		});
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			resetWidgets();
		}
	}

	// Low priority so we read the searching flag after Quest Helper (and the game) have
	// set it for the current build.
	@Subscribe(priority = -5)
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			bankSearching = client.getIntStack()[client.getIntStackSize() - 1] == 1;
			return;
		}

		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}

		removeAddedWidgets();

		if (!shouldOrganise())
		{
			layoutApplied = false;
			return;
		}

		Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (itemContainer == null)
		{
			layoutApplied = false;
			return;
		}

		Widget[] children = itemContainer.getChildren();
		if (children != null && originalContainerChildren == -1)
		{
			originalContainerChildren = children.length;
		}

		Widget[] containerChildren = itemContainer.getDynamicChildren();
		clientThread.invokeAtTickEnd(() -> layoutBank(itemContainer, containerChildren));
	}

	@Subscribe(priority = -1)
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!layoutApplied || event.getParam1() != InterfaceID.Bankmain.ITEMS)
		{
			return;
		}

		MenuEntry menu = event.getMenuEntry();
		Widget w = menu.getWidget();
		if (w == null)
		{
			return;
		}

		// A missing loadout item's "Details" tells you what you still need.
		LoadoutItem loadoutItem = loadoutWidgets.get(w);
		if (loadoutItem != null && "Details".equals(event.getMenuOption()))
		{
			event.consume();
			postLoadoutDetails(loadoutItem);
			return;
		}

		// Remap the menu's widget index to the item's real bank slot so withdraws work in
		// the custom layout (reused real-section AND owned-loadout item widgets).
		if (w.getItemId() > -1)
		{
			ItemContainer bank = client.getItemContainer(InventoryID.BANK);
			if (bank == null)
			{
				return;
			}
			int idx = bank.find(w.getItemId());
			if (idx > -1 && menu.getParam0() != idx)
			{
				menu.setParam0(idx);
			}
		}
	}

	/** Organise only the plain all-items view, and never while the bank is searching. */
	private boolean shouldOrganise()
	{
		return button.isOrganised()
			&& !bankSearching
			&& client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == MAIN_TAB;
	}

	private void layoutBank(Widget itemContainer, Widget[] containerChildren)
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null || containerChildren == null)
		{
			layoutApplied = false;
			return;
		}

		hideBankWidgets(itemContainer, containerChildren);
		loadoutWidgets.clear();

		// Loadout items and the real sections both reuse the bank's own item widgets (the only
		// way item opacity/greying and native withdraw work). The pool is limited, so we draw
		// the REAL sections first — they must never lose a widget — and give loadouts (which
		// are visually placed at the top) the remaining widgets.
		List<Loadout> loadouts = button.isShowLoadouts()
			? loadoutManager.getLoadouts() : Collections.emptyList();
		int loadoutHeight = computeLoadoutHeight(loadouts);

		currentWidgetToUse = 0;

		// 1. Real, auto-categorised sections, positioned BELOW the loadout area.
		int totalSectionsHeight = loadoutHeight;
		for (BankSection section : buildSections(bank))
		{
			totalSectionsHeight = addSection(itemContainer, bank, section, totalSectionsHeight);
		}

		// 2. Loadout sections, positioned at the top, using the spare widgets. Their qty/tick
		// overlays are collected and drawn last so they sit on top of the items.
		List<OverlayText> overlays = new ArrayList<>();
		int loadoutTop = 0;
		for (Loadout loadout : loadouts)
		{
			loadoutTop = addLoadoutSection(itemContainer, bank, loadout, loadoutTop, overlays);
		}

		// 3. Loadout qty/tick overlays.
		for (OverlayText overlay : overlays)
		{
			addedWidgets.add(createText(itemContainer, overlay.text, OVERLAY_TEXT_COLOR,
				ITEM_HORIZONTAL_SPACING, TEXT_HEIGHT - 3, overlay.x, overlay.y));
			addedWidgets.add(createSprite(itemContainer, overlay.spriteId, overlay.spriteX, overlay.spriteY));
		}

		Widget bankItemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankItemContainer == null)
		{
			return;
		}
		int itemContainerHeight = bankItemContainer.getHeight();
		bankItemContainer.setScrollHeight(Math.max(totalSectionsHeight, itemContainerHeight));

		final int itemContainerScroll = bankItemContainer.getScrollY();
		clientThread.invokeLater(() ->
			client.runScript(ScriptID.UPDATE_SCROLLBAR,
				InterfaceID.Bankmain.SCROLLBAR,
				InterfaceID.Bankmain.ITEMS,
				itemContainerScroll));

		layoutApplied = true;
	}

	/**
	 * Groups every distinct item in the bank (in bank order) into named sections, then
	 * emits them in {@link BankCategoriser#sectionOrder()} order. Empty sections are skipped.
	 */
	private List<BankSection> buildSections(ItemContainer bank)
	{
		Map<String, BankSection> byName = new LinkedHashMap<>();

		Set<Integer> seen = new LinkedHashSet<>();
		for (Item item : bank.getItems())
		{
			int id = item.getId();
			if (id <= 0 || id == ItemID.BANK_FILLER || id == ItemID.BLANKOBJECT)
			{
				continue;
			}
			if (!seen.add(id))
			{
				continue;
			}
			String section = categoriser.sectionFor(id);
			byName.computeIfAbsent(section, BankSection::new).add(id);
		}

		List<BankSection> sections = new ArrayList<>();
		Set<String> emitted = new LinkedHashSet<>();
		for (String name : categoriser.sectionOrder())
		{
			BankSection section = byName.get(name);
			if (section != null && !section.isEmpty())
			{
				sections.add(section);
				emitted.add(name);
			}
		}
		// Safety net: a section name not covered by sectionOrder() (shouldn't happen).
		for (Map.Entry<String, BankSection> e : byName.entrySet())
		{
			if (!emitted.contains(e.getKey()) && !e.getValue().isEmpty())
			{
				sections.add(e.getValue());
			}
		}

		// Alphabetise the items within each section.
		Comparator<Integer> byItemName = Comparator.comparing(this::itemName, String.CASE_INSENSITIVE_ORDER);
		for (BankSection section : sections)
		{
			section.getItemIds().sort(byItemName);
		}
		return sections;
	}

	private String itemName(int itemId)
	{
		ItemComposition def = client.getItemDefinition(itemId);
		return def == null || def.getName() == null ? "" : def.getName();
	}

	private int addSection(Widget itemContainer, ItemContainer bank, BankSection section, int totalSectionsHeight)
	{
		int newHeight = addSectionHeader(itemContainer, section.getName(), totalSectionsHeight);

		Widget bankItemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankItemContainer == null)
		{
			return newHeight;
		}

		int itemsAdded = 0;
		for (int itemId : section.getItemIds())
		{
			Widget c = bankItemContainer.getChild(currentWidgetToUse);
			if (c == null)
			{
				return newHeight;
			}
			drawItem(c, itemId, bank);
			placeItem(c, itemsAdded, newHeight);
			currentWidgetToUse++;
			itemsAdded++;
		}

		int rows = itemsAdded / ITEMS_PER_ROW + (itemsAdded % ITEMS_PER_ROW != 0 ? 1 : 0);
		return newHeight + rows * ITEM_VERTICAL_SPACING;
	}

	private void drawItem(Widget c, int itemId, ItemContainer bank)
	{
		int qty = bank.count(itemId);
		ItemComposition def = client.getItemDefinition(itemId);

		c.setItemId(itemId);
		c.setItemQuantity(qty);
		c.setItemQuantityMode(ItemQuantityMode.ALWAYS);

		// Effectively avoid dragging
		c.setDragDeadTime(1000);

		c.setName("<col=ff9040>" + def.getName() + "</col>");
		c.clearActions();

		// Jagex placeholder
		if (def.getPlaceholderTemplateId() >= 0 && def.getPlaceholderId() >= 0)
		{
			c.setOpacity(120);
			c.setAction(8 - 1, "Release");
			c.setAction(10 - 1, "Examine");
		}
		else
		{
			applyWithdrawActions(c, def);
			c.setOpacity(0);
		}

		setDragScroll(c);
		c.setHidden(false);
		c.revalidate();
	}

	/** The standard bank withdraw / placeholder / examine actions for an owned item. */
	private void applyWithdrawActions(Widget c, ItemComposition def)
	{
		int quantityType = client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE);
		int requestQty = client.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY);
		String suffix;
		switch (quantityType)
		{
			default:
				suffix = "1";
				break;
			case 1:
				suffix = "5";
				break;
			case 2:
				suffix = "10";
				break;
			case 3:
				suffix = Integer.toString(Math.max(1, requestQty));
				break;
			case 4:
				suffix = "All";
				break;
		}
		c.setAction(0, "Withdraw-" + suffix);
		if (quantityType != 0)
		{
			c.setAction(1, "Withdraw-1");
		}
		c.setAction(2, "Withdraw-5");
		c.setAction(3, "Withdraw-10");
		if (requestQty > 0)
		{
			c.setAction(4, "Withdraw-" + requestQty);
		}
		c.setAction(5, "Withdraw-X");
		c.setAction(6, "Withdraw-All");
		c.setAction(7, "Withdraw-All-but-1");
		if (client.getVarbitValue(VarbitID.BANK_BANKOPS_TOGGLE_ON) == 1 && def.getIntValue(ParamID.BANK_AUTOCHARGE) != -1)
		{
			c.setAction(8, "Configure-Charges");
		}
		if (client.getVarbitValue(VarbitID.BANK_LEAVEPLACEHOLDERS) == 0)
		{
			c.setAction(9, "Placeholder");
		}
		c.setAction(10, "Examine");
	}

	private void setDragScroll(Widget c)
	{
		c.setOnDragListener(ScriptID.BANKMAIN_DRAGSCROLL, ScriptEvent.WIDGET_ID, ScriptEvent.WIDGET_INDEX,
			ScriptEvent.MOUSE_X, ScriptEvent.MOUSE_Y, InterfaceID.Bankmain.SCROLLBAR, 0);
		c.setOnDragCompleteListener((JavaScriptCallback) ev -> {});
	}

	private void placeItem(Widget widget, int itemsAdded, int totalSectionsHeight)
	{
		int adjYOffset = totalSectionsHeight + (itemsAdded / ITEMS_PER_ROW) * ITEM_VERTICAL_SPACING;
		int adjXOffset = (itemsAdded % ITEMS_PER_ROW) * ITEM_HORIZONTAL_SPACING + ITEM_ROW_START;

		if (widget.getOriginalY() != adjYOffset)
		{
			widget.setOriginalY(adjYOffset);
			widget.revalidate();
		}
		if (widget.getOriginalX() != adjXOffset)
		{
			widget.setOriginalX(adjXOffset);
			widget.revalidate();
		}
	}

	private int addSectionHeader(Widget itemContainer, String title, int totalSectionsHeight)
	{
		addedWidgets.add(createLine(itemContainer, SpriteID.TRADEBACKING_DARK, ITEM_ROW_START, totalSectionsHeight));
		addedWidgets.add(createText(itemContainer, title, SECTION_HEADER_COLOR,
			(ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING) + ITEM_ROW_START, TEXT_HEIGHT,
			ITEM_ROW_START, totalSectionsHeight + LINE_VERTICAL_SPACING));

		return totalSectionsHeight + LINE_VERTICAL_SPACING + TEXT_HEIGHT;
	}

	/** Height the loadout area will occupy, so the real sections can be offset below it. */
	private int computeLoadoutHeight(List<Loadout> loadouts)
	{
		int height = 0;
		for (Loadout loadout : loadouts)
		{
			if (loadout.isEmpty())
			{
				continue;
			}
			height += LINE_VERTICAL_SPACING + TEXT_HEIGHT; // header
			int rows = (loadout.getItems().size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
			height += rows * ITEM_VERTICAL_SPACING;
		}
		return height;
	}

	private int addLoadoutSection(Widget itemContainer, ItemContainer bank, Loadout loadout,
		int totalSectionsHeight, List<OverlayText> overlays)
	{
		if (loadout.isEmpty())
		{
			return totalSectionsHeight;
		}

		int newHeight = addSectionHeader(itemContainer, loadout.getName(), totalSectionsHeight);

		Widget bankItemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankItemContainer == null)
		{
			return newHeight;
		}

		int itemsAdded = 0;
		for (LoadoutItem li : loadout.getItems())
		{
			Widget c = bankItemContainer.getChild(currentWidgetToUse);
			if (c == null)
			{
				break; // out of spare bank widgets
			}
			int have = bank.count(li.getItemId());
			drawLoadoutItem(c, li, have);
			placeItem(c, itemsAdded, newHeight);
			makeOverlay(have, li.getQuantity(), c.getOriginalX(), c.getOriginalY(), overlays);
			loadoutWidgets.put(c, li);
			currentWidgetToUse++;
			itemsAdded++;
		}

		int rows = itemsAdded / ITEMS_PER_ROW + (itemsAdded % ITEMS_PER_ROW != 0 ? 1 : 0);
		return newHeight + rows * ITEM_VERTICAL_SPACING;
	}

	/**
	 * Draws a loadout entry onto a reused bank item widget: the owned item (withdrawable)
	 * if you have it, or a greyed placeholder if you're missing it. Reusing the bank's own
	 * widget — rather than a created one — is what makes opacity/greying, hover and native
	 * withdraw work.
	 */
	private void drawLoadoutItem(Widget c, LoadoutItem li, int have)
	{
		ItemComposition def = client.getItemDefinition(li.getItemId());

		c.setItemId(li.getItemId());
		c.setItemQuantity(Math.max(0, have));
		c.setItemQuantityMode(ItemQuantityMode.ALWAYS);
		c.setName("<col=ff9040>" + (def == null ? li.getName() : def.getName()) + "</col>");
		c.setDragDeadTime(1000);
		c.clearActions();

		if (have > 0 && def != null)
		{
			applyWithdrawActions(c, def);
			c.setOpacity(0);
		}
		else
		{
			c.setOpacity(120);
			c.setAction(0, "Details");
		}

		setDragScroll(c);
		c.setHidden(false);
		c.revalidate();
	}

	/** Builds the "/ goal" text + tick/cross overlay for a loadout item (à la Quest Helper). */
	private void makeOverlay(int have, int goal, int baseX, int baseY, List<OverlayText> overlays)
	{
		String goalStr = QuantityFormatter.quantityToStackSize(goal);
		int requirementLength = (int) Math.round(goalStr.length() * 5.5);
		int extraLength = QuantityFormatter.quantityToStackSize(Math.max(0, have)).length() * 6;

		int xPos = baseX + 2 + extraLength;
		int yPos = baseY - 1;
		if (extraLength + requirementLength > 20)
		{
			xPos = baseX;
			yPos = baseY + 9;
		}
		int spriteX = xPos + requirementLength + 10;
		int spriteY = yPos;
		if (yPos != baseY - 1)
		{
			spriteX = baseX + 2 + extraLength;
			spriteY = baseY - 1;
		}

		int sprite = have >= goal ? TICK_SPRITE : CROSS_SPRITE;
		overlays.add(new OverlayText("/ " + goalStr, xPos, yPos, sprite, spriteX, spriteY));
	}

	private void postLoadoutDetails(LoadoutItem li)
	{
		ItemComposition def = client.getItemDefinition(li.getItemId());
		String name = def == null ? li.getName() : def.getName();
		String message = new ChatMessageBuilder()
			.append("You need ")
			.append(ChatColorType.HIGHLIGHT)
			.append(li.getQuantity() + " x " + Text.removeTags(name))
			.append(ChatColorType.NORMAL)
			.append(".")
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.ITEM_EXAMINE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	private Widget createSprite(Widget container, int spriteId, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(10);
		widget.setOriginalHeight(10);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	private void hideBankWidgets(Widget itemContainer, Widget[] containerChildren)
	{
		for (int i = 0; i < containerChildren.length; ++i)
		{
			Widget widget = itemContainer.getChild(i);
			if (widget == null)
			{
				continue;
			}

			boolean isItem = widget.getItemId() > -1 && widget.getItemId() != ItemID.BLANKOBJECT;
			boolean isSeparator = widget.getSpriteId() == SpriteID.TRADEBACKING_DARK || widget.getText().contains("Tab");
			if (!widget.isSelfHidden() && (isItem || isSeparator))
			{
				widget.setHidden(true);
			}
		}
	}

	private void resetWidgets()
	{
		// Bank item children sizes are only set when the bank is first opened, so we
		// reset them on each rebuild after our layout shrank/moved them.
		Widget w = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (w == null || w.getChildren() == null)
		{
			return;
		}

		for (Widget c : w.getChildren())
		{
			if (c.getOriginalHeight() < BANK_ITEM_HEIGHT)
			{
				break;
			}
			if (c.getOriginalWidth() != BANK_ITEM_WIDTH || c.getOriginalHeight() != BANK_ITEM_HEIGHT)
			{
				c.setOriginalWidth(BANK_ITEM_WIDTH);
				c.setOriginalHeight(BANK_ITEM_HEIGHT);
				c.revalidate();
			}
		}
	}

	private void removeAddedWidgets()
	{
		if (originalContainerChildren == -1 || addedWidgets.isEmpty())
		{
			return;
		}
		Widget parent = addedWidgets.get(0).getParent();
		if (parent == null || parent.getChildren() == null)
		{
			addedWidgets.clear();
			return;
		}
		parent.setChildren(Arrays.copyOf(parent.getChildren(), originalContainerChildren));
		parent.revalidate();
		addedWidgets.clear();
	}

	private Widget createLine(Widget container, int spriteId, int x, int y)
	{
		final int width = ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING;
		Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(LINE_HEIGHT);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	private Widget createText(Widget container, String text, int color, int width, int height, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setText(text);
		widget.setFontId(FontID.PLAIN_11);
		widget.setTextColor(color);
		widget.setTextShadowed(true);
		widget.revalidate();
		return widget;
	}

	/** A deferred "/ goal" text + tick/cross sprite drawn over a loadout item. */
	private static final class OverlayText
	{
		private final String text;
		private final int x;
		private final int y;
		private final int spriteId;
		private final int spriteX;
		private final int spriteY;

		private OverlayText(String text, int x, int y, int spriteId, int spriteX, int spriteY)
		{
			this.text = text;
			this.x = x;
			this.y = y;
			this.spriteId = spriteId;
			this.spriteX = spriteX;
			this.spriteY = spriteY;
		}
	}
}
