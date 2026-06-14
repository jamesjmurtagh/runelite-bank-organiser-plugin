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
package com.bankorganiser.category;

import com.bankorganiser.BankOrganiserConfig;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

public class BankCategoriserTest
{
	@Mock
	private ItemManager itemManager;
	@Mock
	private BankOrganiserConfig config;

	private BankCategoriser categoriser;
	private int nextId = 1;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		lenient().when(config.categoryOverrides()).thenReturn("");
		categoriser = new BankCategoriser(itemManager, config);
	}

	/** Registers a non-equippable item with the given name (stackable by default). */
	private int item(String name)
	{
		return item(name, false);
	}

	private int item(String name, boolean stackable)
	{
		int id = nextId++;
		ItemComposition comp = org.mockito.Mockito.mock(ItemComposition.class);
		lenient().when(comp.getName()).thenReturn(name);
		lenient().when(comp.isStackable()).thenReturn(stackable);
		when(itemManager.canonicalize(id)).thenReturn(id);
		when(itemManager.getItemComposition(id)).thenReturn(comp);
		lenient().when(itemManager.getItemStats(id)).thenReturn(null);
		return id;
	}

	/** Registers an equippable item occupying the given equipment slot. */
	private int equip(String name, EquipmentInventorySlot slot)
	{
		int id = nextId++;
		ItemComposition comp = org.mockito.Mockito.mock(ItemComposition.class);
		lenient().when(comp.getName()).thenReturn(name);
		when(itemManager.canonicalize(id)).thenReturn(id);
		when(itemManager.getItemComposition(id)).thenReturn(comp);
		ItemEquipmentStats equipment = ItemEquipmentStats.builder().slot(slot.getSlotIdx()).build();
		ItemStats stats = new ItemStats(true, 0.0, 0, equipment);
		lenient().when(itemManager.getItemStats(id)).thenReturn(stats);
		return id;
	}

	private Category categorise(int id)
	{
		return categoriser.categorise(id);
	}

	@Test
	public void meleeIsTheEquipmentSlotFallback()
	{
		assertEquals(Category.MELEE_EQUIPMENT, categorise(equip("Rune platebody", EquipmentInventorySlot.BODY)));
		assertEquals(Category.MELEE_EQUIPMENT, categorise(equip("Dragon dagger", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.MELEE_EQUIPMENT, categorise(equip("Abyssal whip", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.MELEE_EQUIPMENT, categorise(equip("Rune full helm", EquipmentInventorySlot.HEAD)));
		assertEquals(Category.MELEE_EQUIPMENT, categorise(equip("Dragon kiteshield", EquipmentInventorySlot.SHIELD)));
	}

	@Test
	public void rangedGear()
	{
		assertEquals(Category.RANGED_EQUIPMENT, categorise(equip("Magic shortbow", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.RANGED_EQUIPMENT, categorise(equip("Rune crossbow", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.RANGED_EQUIPMENT, categorise(equip("Toxic blowpipe", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.RANGED_EQUIPMENT, categorise(item("Black chinchompa", true)));
		assertEquals(Category.RANGED_EQUIPMENT, categorise(item("Rune arrow", true)));
		assertEquals(Category.RANGED_EQUIPMENT, categorise(item("Adamant dart", true)));
		assertEquals(Category.RANGED_EQUIPMENT, categorise(item("Runite bolts", true)));
		assertEquals(Category.RANGED_EQUIPMENT, categorise(equip("Green d'hide body", EquipmentInventorySlot.BODY)));
		assertEquals(Category.RANGED_EQUIPMENT, categorise(equip("Leather chaps", EquipmentInventorySlot.LEGS)));
	}

	@Test
	public void mageGear()
	{
		assertEquals(Category.MAGE_EQUIPMENT, categorise(item("Air rune", true)));
		assertEquals(Category.MAGE_EQUIPMENT, categorise(item("Death rune", true)));
		assertEquals(Category.MAGE_EQUIPMENT, categorise(equip("Staff of fire", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.MAGE_EQUIPMENT, categorise(equip("Ancient staff", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.MAGE_EQUIPMENT, categorise(equip("Mystic robe top", EquipmentInventorySlot.BODY)));
		assertEquals(Category.MAGE_EQUIPMENT, categorise(equip("Ahrim's robetop", EquipmentInventorySlot.BODY)));
	}

	@Test
	public void runecrafting()
	{
		assertEquals(Category.RUNECRAFTING, categorise(item("Pure essence", true)));
		assertEquals(Category.RUNECRAFTING, categorise(item("Rune essence", true)));
		assertEquals(Category.RUNECRAFTING, categorise(item("Air talisman")));
		assertEquals(Category.RUNECRAFTING, categorise(item("Earth tiara")));
		assertEquals(Category.RUNECRAFTING, categorise(item("Large pouch")));
	}

	@Test
	public void miningAndSmithing()
	{
		assertEquals(Category.MINING_SMITHING, categorise(equip("Rune pickaxe", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.MINING_SMITHING, categorise(item("Iron ore")));
		assertEquals(Category.MINING_SMITHING, categorise(item("Runite bar")));
		assertEquals(Category.MINING_SMITHING, categorise(item("Coal")));
		assertEquals(Category.MINING_SMITHING, categorise(item("Hammer")));
	}

	@Test
	public void woodcutting()
	{
		assertEquals(Category.WOODCUTTING, categorise(equip("Dragon axe", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.WOODCUTTING, categorise(equip("Rune axe", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.WOODCUTTING, categorise(item("Yew logs")));
		assertEquals(Category.WOODCUTTING, categorise(item("Logs")));
		// A battleaxe is NOT a woodcutting axe.
		assertEquals(Category.MELEE_EQUIPMENT, categorise(equip("Dragon battleaxe", EquipmentInventorySlot.WEAPON)));
	}

	@Test
	public void fishing()
	{
		assertEquals(Category.FISHING, categorise(item("Lobster pot")));
		assertEquals(Category.FISHING, categorise(item("Small fishing net")));
		assertEquals(Category.FISHING, categorise(equip("Dragon harpoon", EquipmentInventorySlot.WEAPON)));
		assertEquals(Category.FISHING, categorise(item("Raw shark")));
		assertEquals(Category.FISHING, categorise(item("Raw lobster")));
		// Raw meat is not fish.
		assertEquals(Category.OTHER, categorise(item("Raw beef")));
	}

	@Test
	public void farmingAndSeeds()
	{
		assertEquals(Category.HERB_SEEDS, categorise(item("Ranarr seed")));
		assertEquals(Category.HERB_SEEDS, categorise(item("Snapdragon seed")));
		assertEquals(Category.TREE_SEEDS, categorise(item("Acorn")));
		assertEquals(Category.TREE_SEEDS, categorise(item("Yew seed")));
		assertEquals(Category.TREE_SEEDS, categorise(item("Magic seed")));
		assertEquals(Category.TREE_SEEDS, categorise(item("Palm tree seed")));
		assertEquals(Category.FARMING, categorise(item("Potato seed")));
		assertEquals(Category.FARMING, categorise(item("Rake")));
		assertEquals(Category.FARMING, categorise(item("Watering can")));
		assertEquals(Category.FARMING, categorise(item("Supercompost")));
		assertEquals(Category.FARMING, categorise(item("Oak sapling")));
	}

	@Test
	public void chargedJewelleryIsNotMistakenForPotions()
	{
		// "(n)" charge suffix must not be read as a potion dose.
		assertEquals(Category.JEWELLERY, categorise(item("Ring of dueling(8)")));
		assertEquals(Category.JEWELLERY, categorise(item("Games necklace(8)")));
		assertEquals(Category.JEWELLERY, categorise(item("Amulet of glory(4)")));
		assertEquals(Category.JEWELLERY, categorise(item("Combat bracelet(6)")));
		// A real potion dose is still a potion.
		assertEquals(Category.POTIONS, categorise(item("Prayer potion(4)")));
	}

	@Test
	public void herbsPotionsFood()
	{
		assertEquals(Category.HERBS, categorise(item("Grimy ranarr weed")));
		assertEquals(Category.HERBS, categorise(item("Snapdragon")));
		assertEquals(Category.POTIONS, categorise(item("Prayer potion(4)")));
		assertEquals(Category.POTIONS, categorise(item("Saradomin brew(2)")));
		assertEquals(Category.FOOD, categorise(item("Shark")));
		assertEquals(Category.FOOD, categorise(item("Monkfish")));
	}

	@Test
	public void teleports()
	{
		assertEquals(Category.TELEPORTS, categorise(item("Varrock teleport", true)));
		assertEquals(Category.TELEPORTS, categorise(item("Ectophial")));
		assertEquals(Category.TELEPORTS, categorise(equip("Ardougne cloak 4", EquipmentInventorySlot.CAPE)));
		assertEquals(Category.TELEPORTS, categorise(item("Teleport to house", true)));
	}

	@Test
	public void jewelleryGemsCurrencyEquipment()
	{
		assertEquals(Category.JEWELLERY, categorise(equip("Amulet of fury", EquipmentInventorySlot.AMULET)));
		assertEquals(Category.JEWELLERY, categorise(equip("Berserker ring", EquipmentInventorySlot.RING)));
		assertEquals(Category.JEWELLERY, categorise(item("Combat bracelet")));
		assertEquals(Category.GEMS, categorise(item("Uncut diamond")));
		assertEquals(Category.GEMS, categorise(item("Ruby")));
		assertEquals(Category.CURRENCY, categorise(item("Coins", true)));
		// A slot with no style keyword (boots) falls through to the generic Equipment section.
		assertEquals(Category.EQUIPMENT, categorise(equip("Dragon boots", EquipmentInventorySlot.BOOTS)));
	}

	@Test
	public void fallbacks()
	{
		assertEquals(Category.RESOURCES, categorise(item("Feather", true)));
		assertEquals(Category.OTHER, categorise(item("Bones")));
	}

	// --- Overrides -----------------------------------------------------------------

	@Test
	public void overrideBeatsTheBuiltInCategoriser()
	{
		int scimitar = equip("Rune scimitar", EquipmentInventorySlot.WEAPON);
		when(config.categoryOverrides()).thenReturn("scimitar=My Weapons");

		assertEquals(Category.MELEE_EQUIPMENT, categoriser.categorise(scimitar));
		assertEquals("My Weapons", categoriser.sectionFor(scimitar));
	}

	@Test
	public void overrideByItemId()
	{
		int id = item("Some Gizmo");
		when(config.categoryOverrides()).thenReturn(id + "=Tools");
		assertEquals("Tools", categoriser.sectionFor(id));
	}

	@Test
	public void overrideOntoBuiltinNameIsCanonicalAndNotCustom()
	{
		int log = item("Yew logs");
		when(config.categoryOverrides()).thenReturn("yew logs=woodcutting"); // lower-case -> canonical
		assertEquals(Category.WOODCUTTING.getDisplayName(), categoriser.sectionFor(log));
		assertEquals(Category.values()[0].getDisplayName(), categoriser.sectionOrder().get(0));
	}

	@Test
	public void customSectionsComeFirstInConfigOrder()
	{
		when(config.categoryOverrides()).thenReturn("pickaxe=Mining\nchisel=Tools");
		assertEquals("Mining", categoriser.sectionOrder().get(0));
		assertEquals("Tools", categoriser.sectionOrder().get(1));
		assertTrue(categoriser.sectionOrder().indexOf("Mining")
			< categoriser.sectionOrder().indexOf(Category.MELEE_EQUIPMENT.getDisplayName()));
	}

	@Test
	public void earlierOverrideLinesWin()
	{
		int pickaxe = equip("Rune pickaxe", EquipmentInventorySlot.WEAPON);
		when(config.categoryOverrides()).thenReturn("rune=Rune stuff\npickaxe=Mining");
		assertEquals("Rune stuff", categoriser.sectionFor(pickaxe));
	}

	@Test
	public void blankAndCommentLinesAreIgnored()
	{
		int pickaxe = equip("Rune pickaxe", EquipmentInventorySlot.WEAPON);
		when(config.categoryOverrides()).thenReturn("\n# a comment\n   \npickaxe=Mining\n");
		assertEquals("Mining", categoriser.sectionFor(pickaxe));
	}

	@Test
	public void regexOverrideMatchesByPattern()
	{
		int fireRune = item("Fire rune", true);
		int cosmicRune = item("Cosmic rune", true);
		int runeScimitar = equip("Rune scimitar", EquipmentInventorySlot.WEAPON);
		when(config.categoryOverrides()).thenReturn("/\\w+ rune/=Runes");

		assertEquals("Runes", categoriser.sectionFor(fireRune));
		assertEquals("Runes", categoriser.sectionFor(cosmicRune));
		// "rune scimitar" has no "<word> rune" so the pattern must not catch it.
		assertEquals(Category.MELEE_EQUIPMENT.getDisplayName(), categoriser.sectionFor(runeScimitar));
		// The custom section appears first.
		assertEquals("Runes", categoriser.sectionOrder().get(0));
	}

	@Test
	public void regexCanBeAnchored()
	{
		int coal = item("Coal", true);
		int coalBag = item("Coal bag", false);
		when(config.categoryOverrides()).thenReturn("/^coal$/=Fuel");
		assertEquals("Fuel", categoriser.sectionFor(coal));
		// Anchored pattern must not match "coal bag" (it falls through to the built-in rules).
		assertEquals(Category.OTHER.getDisplayName(), categoriser.sectionFor(coalBag));
	}

	@Test
	public void invalidRegexLineIsIgnored()
	{
		int fireRune = item("Fire rune", true);
		// Unclosed group is invalid; the line is dropped and the item categorises normally.
		when(config.categoryOverrides()).thenReturn("/[/=Runes");
		assertEquals(Category.MAGE_EQUIPMENT.getDisplayName(), categoriser.sectionFor(fireRune));
	}

	@Test
	public void literalSubstringWithRegexCharsIsNotTreatedAsRegex()
	{
		// A bare (no slashes) match with regex metacharacters stays a literal substring.
		int prayerPot = item("Prayer potion(4)", false);
		when(config.categoryOverrides()).thenReturn("potion(4)=My Potions");
		assertEquals("My Potions", categoriser.sectionFor(prayerPot));
	}
}
