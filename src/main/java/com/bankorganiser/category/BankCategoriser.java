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
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Decides which section a bank item belongs to.
 *
 * <p>{@link #sectionFor(int)} is the entry point used for layout: it applies the user's
 * configured overrides first, then falls back to {@link #categorise(int)}.
 *
 * <p>{@link #categorise(int)} is an ordered, first-match-wins rule engine. Crucially the
 * skill/style <em>name</em> rules run before the equipment slot: a pickaxe is a
 * weapon-slot item but is filed under Mining, a mystic robe is a body-slot item but is
 * filed under Mage. Equipment slot is only the <em>fallback</em> — residual weapons and
 * body armour default to Melee, which is why plate gear lands there without needing a
 * keyword for every tier. Items are canonicalised first so notes/placeholders resolve to
 * their base item. The keyword lists are best-effort, not exhaustive; the Category
 * overrides setting exists to correct anything they get wrong.
 */
@Slf4j
@Singleton
public class BankCategoriser
{
	private static final int WEAPON_SLOT = EquipmentInventorySlot.WEAPON.getSlotIdx();
	private static final int AMMO_SLOT = EquipmentInventorySlot.AMMO.getSlotIdx();
	private static final int AMULET_SLOT = EquipmentInventorySlot.AMULET.getSlotIdx();
	private static final int RING_SLOT = EquipmentInventorySlot.RING.getSlotIdx();
	private static final int HEAD_SLOT = EquipmentInventorySlot.HEAD.getSlotIdx();
	private static final int BODY_SLOT = EquipmentInventorySlot.BODY.getSlotIdx();
	private static final int LEGS_SLOT = EquipmentInventorySlot.LEGS.getSlotIdx();
	private static final int SHIELD_SLOT = EquipmentInventorySlot.SHIELD.getSlotIdx();

	private static final Pattern DOSE_PATTERN = Pattern.compile(".*\\(\\d\\)$");
	private static final Pattern ITEM_ID = Pattern.compile("\\d+");
	// Trailing charge/dose suffix, e.g. "(8)" on "Ring of dueling(8)".
	private static final Pattern CHARGE_SUFFIX = Pattern.compile("\\s*\\(\\d+\\)$");

	// --- Combat: Mage ---------------------------------------------------------------
	private static final Set<String> MAGE_KEYWORDS = ImmutableSet.of(
		"staff", "staves", "battlestaff", "wand", "trident", "mystic", "wizard", "robe",
		"infinity", "ahrim", "splitbark", "ancestral", "virtus", "dagon'hai", "zuriel",
		"skeletal", "occult", "mage's book", "tome of", "farseer", "kodai",
		"nightmare staff", "sanguinesti", "bryophyta", "void mage", "elder chaos",
		"enchanted hat", "enchanted top", "enchanted robe");

	// --- Combat: Ranged -------------------------------------------------------------
	private static final Set<String> RANGED_KEYWORDS = ImmutableSet.of(
		"d'hide", "dragonhide", "leather", "coif", "snakeskin", "robin hood", "ranger",
		"ava's", "karil", "spined", "crystal helm", "crystal body", "crystal legs",
		"armadyl helmet", "armadyl chestplate", "armadyl chainskirt", "pegasian",
		"3rd age range");

	// --- Runecrafting ---------------------------------------------------------------
	private static final Set<String> RC_POUCHES = ImmutableSet.of(
		"small pouch", "medium pouch", "large pouch", "giant pouch", "colossal pouch");

	// --- Teleports ------------------------------------------------------------------
	private static final Set<String> TELEPORT_KEYWORDS = ImmutableSet.of(
		"teleport", "ectophial", "ardougne cloak", "enchanted lyre", "pharaoh's sceptre",
		"skull sceptre", "royal seed pod", "digsite pendant", "drakan's medallion",
		"camulet");

	// --- Seeds ----------------------------------------------------------------------
	private static final Set<String> HERB_SEEDS = ImmutableSet.of(
		"guam seed", "marrentill seed", "tarromin seed", "harralander seed", "ranarr seed",
		"toadflax seed", "irit seed", "avantoe seed", "kwuarm seed", "snapdragon seed",
		"cadantine seed", "lantadyme seed", "dwarf weed seed", "torstol seed", "huasca seed");

	private static final Set<String> TREE_SEEDS = ImmutableSet.of(
		"acorn", "willow seed", "maple seed", "yew seed", "magic seed", "redwood seed",
		"spirit seed", "teak seed", "mahogany seed", "crystal acorn", "apple tree seed",
		"banana tree seed", "orange tree seed", "curry tree seed", "pineapple seed",
		"papaya tree seed", "palm tree seed", "dragonfruit tree seed", "calquat tree seed",
		"celastrus seed");

	// --- Farming --------------------------------------------------------------------
	private static final Set<String> FARM_TOOLS = ImmutableSet.of(
		"rake", "spade", "seed dibber", "secateurs", "magic secateurs", "gardening trowel",
		"trowel", "plant cure", "fruit basket", "seed box", "auto-weed", "scarecrow");

	// --- Fishing --------------------------------------------------------------------
	private static final Set<String> RAW_MEAT = ImmutableSet.of(
		"raw beef", "raw chicken", "raw rat meat", "raw bear meat", "raw ugthanki meat",
		"raw oomlie meat", "raw rabbit", "raw pheasant", "raw bird meat", "raw chompy",
		"raw jubbly", "raw boar meat", "raw beast meat", "raw yak meat");

	// --- Herbs / Potions / Food / Gems ----------------------------------------------
	private static final Set<String> POTION_KEYWORDS = ImmutableSet.of(
		"potion", "brew", "mix", "antifire", "antipoison", "antidote", "restore",
		"serum", "elixir", "venom", "antivenom", "stamina", "battlemage", "bastion");

	private static final Set<String> CLEAN_HERBS = ImmutableSet.of(
		"guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed",
		"toadflax", "irit leaf", "avantoe", "kwuarm", "snapdragon", "cadantine",
		"lantadyme", "dwarf weed", "torstol", "huasca");

	private static final Set<String> GEM_NAMES = ImmutableSet.of(
		"opal", "jade", "red topaz", "sapphire", "emerald", "ruby", "diamond",
		"dragonstone", "onyx", "zenyte");

	private static final Set<String> FOOD_NAMES = ImmutableSet.of(
		"shrimps", "anchovies", "sardine", "herring", "mackerel", "trout", "cod",
		"pike", "salmon", "tuna", "lobster", "bass", "swordfish", "monkfish",
		"shark", "sea turtle", "manta ray", "anglerfish", "dark crab", "karambwan",
		"bread", "cake", "chocolate cake", "stew", "curry", "tuna potato",
		"potato with cheese", "jug of wine");

	private static final Set<String> FOOD_KEYWORDS = ImmutableSet.of("pizza", " pie", "cooked");

	/** Built-in category display names, lower-cased, for distinguishing custom sections. */
	private static final Set<String> BUILTIN_NAMES_LOWER;

	static
	{
		Set<String> names = new LinkedHashSet<>();
		for (Category c : Category.values())
		{
			names.add(c.getDisplayName().toLowerCase(Locale.ROOT));
		}
		BUILTIN_NAMES_LOWER = names;
	}

	private final ItemManager itemManager;
	private final BankOrganiserConfig config;

	private String cachedOverridesRaw;
	private String cachedOrderRaw;
	private List<Override> overrides = new ArrayList<>();
	private List<String> customSectionOrder = new ArrayList<>();
	private List<String> userCategoryOrder = new ArrayList<>();

	@Inject
	public BankCategoriser(ItemManager itemManager, BankOrganiserConfig config)
	{
		this.itemManager = itemManager;
		this.config = config;
	}

	/**
	 * The section name an item should be filed under — overrides first, then the built-in
	 * categoriser. Returns a display name (a built-in category's name, or a custom one).
	 */
	public String sectionFor(int rawItemId)
	{
		ensureParsed();

		final int itemId = itemManager.canonicalize(rawItemId);
		final ItemComposition comp = itemManager.getItemComposition(itemId);
		final String name = comp == null || comp.getName() == null
			? "" : comp.getName().toLowerCase(Locale.ROOT);

		for (Override override : overrides)
		{
			if (override.matches(itemId, name))
			{
				return override.section;
			}
		}

		return categorise(rawItemId).getDisplayName();
	}

	/**
	 * The order sections are drawn in: the user's configured order first, then any sections
	 * they didn't list in the default order (custom override sections, then the built-in
	 * categories in their fixed order). Sections with no items are skipped by the caller.
	 */
	public List<String> sectionOrder()
	{
		ensureParsed();

		// LinkedHashSet keeps first-seen order and de-duplicates, so a section named in the
		// user's order won't be added again by the default-order pass below.
		LinkedHashSet<String> order = new LinkedHashSet<>(userCategoryOrder);
		order.addAll(customSectionOrder);
		for (Category c : Category.values())
		{
			order.add(c.getDisplayName());
		}
		return new ArrayList<>(order);
	}

	public Category categorise(int rawItemId)
	{
		final int itemId = itemManager.canonicalize(rawItemId);

		final ItemComposition comp = itemManager.getItemComposition(itemId);
		if (comp == null)
		{
			return Category.OTHER;
		}
		final String name = comp.getName() == null ? "" : comp.getName().toLowerCase(Locale.ROOT);

		// Skill / style name rules first — these win over equipment slot.
		if (isCurrency(name))
		{
			return Category.CURRENCY;
		}
		if (isRunecrafting(name))
		{
			return Category.RUNECRAFTING;
		}
		if (isTeleport(name))
		{
			return Category.TELEPORTS;
		}
		if (isMiningSmithing(name))
		{
			return Category.MINING_SMITHING;
		}
		if (isWoodcutting(name))
		{
			return Category.WOODCUTTING;
		}
		if (isFishing(name))
		{
			return Category.FISHING;
		}
		if (HERB_SEEDS.contains(name))
		{
			return Category.HERB_SEEDS;
		}
		if (TREE_SEEDS.contains(name))
		{
			return Category.TREE_SEEDS;
		}
		if (isFarming(name))
		{
			return Category.FARMING;
		}
		if (isHerb(name))
		{
			return Category.HERBS;
		}
		if (isPotion(name))
		{
			return Category.POTIONS;
		}
		if (isFood(name))
		{
			return Category.FOOD;
		}
		if (isMageGear(name))
		{
			return Category.MAGE_EQUIPMENT;
		}
		if (isRangedGear(name))
		{
			return Category.RANGED_EQUIPMENT;
		}
		if (isJewellery(name))
		{
			return Category.JEWELLERY;
		}
		if (isGem(name))
		{
			return Category.GEMS;
		}

		// Equipment-slot fallback: residual weapons/armour default to Melee.
		final Category bySlot = byEquipmentSlotFallback(itemId);
		if (bySlot != null)
		{
			return bySlot;
		}

		return comp.isStackable() ? Category.RESOURCES : Category.OTHER;
	}

	private Category byEquipmentSlotFallback(int itemId)
	{
		final ItemStats stats = itemManager.getItemStats(itemId);
		if (stats == null || !stats.isEquipable())
		{
			return null;
		}
		final ItemEquipmentStats equip = stats.getEquipment();
		if (equip == null)
		{
			return null;
		}
		final int slot = equip.getSlot();
		if (slot == WEAPON_SLOT || slot == BODY_SLOT || slot == LEGS_SLOT
			|| slot == HEAD_SLOT || slot == SHIELD_SLOT)
		{
			return Category.MELEE_EQUIPMENT;
		}
		if (slot == AMMO_SLOT)
		{
			return Category.RANGED_EQUIPMENT;
		}
		if (slot == AMULET_SLOT || slot == RING_SLOT)
		{
			return Category.JEWELLERY;
		}
		return Category.EQUIPMENT;
	}

	// --- Rule predicates ------------------------------------------------------------

	private static boolean isCurrency(String name)
	{
		return name.equals("coins") || name.equals("platinum token")
			|| name.endsWith(" token") || name.endsWith(" tokens");
	}

	private static boolean isRunecrafting(String name)
	{
		return name.contains("talisman") || name.contains("tiara") || name.contains("essence")
			|| RC_POUCHES.contains(name);
	}

	private static boolean isTeleport(String name)
	{
		return containsAny(name, TELEPORT_KEYWORDS);
	}

	private static boolean isMiningSmithing(String name)
	{
		if (name.contains("pickaxe"))
		{
			return true;
		}
		if (name.endsWith(" ore") || name.equals("coal") || name.endsWith(" bar"))
		{
			return true;
		}
		return name.equals("hammer") || name.equals("clay") || name.equals("soft clay");
	}

	private static boolean isWoodcutting(String name)
	{
		if (name.endsWith(" axe") && !name.contains("pickaxe")
			&& !name.contains("throwing axe") && !name.contains("thrownaxe"))
		{
			return true;
		}
		if (name.endsWith("logs") || name.equals("logs"))
		{
			return true;
		}
		return name.contains("forestry") || name.equals("log basket");
	}

	private static boolean isFishing(String name)
	{
		if (name.contains("fishing") || name.contains("harpoon"))
		{
			return true;
		}
		if (name.equals("lobster pot") || name.contains("karambwan vessel"))
		{
			return true;
		}
		return name.startsWith("raw ") && !RAW_MEAT.contains(name);
	}

	private static boolean isFarming(String name)
	{
		if (name.contains("sapling") || name.contains("compost")
			|| name.contains("watering can") || name.contains("plant pot"))
		{
			return true;
		}
		if (FARM_TOOLS.contains(name))
		{
			return true;
		}
		// Herb and tree seeds are matched earlier, so any remaining seed is an "other" seed.
		return name.endsWith("seed") || name.endsWith("seeds");
	}

	private static boolean isHerb(String name)
	{
		return name.startsWith("grimy ") || CLEAN_HERBS.contains(name);
	}

	private static boolean isPotion(String name)
	{
		// Charged jewellery (e.g. "Ring of dueling(8)") also ends in "(n)" — don't mistake it
		// for a potion dose.
		if (isJewellery(name))
		{
			return false;
		}
		if (DOSE_PATTERN.matcher(name).matches())
		{
			return true;
		}
		return containsAny(name, POTION_KEYWORDS);
	}

	private static boolean isFood(String name)
	{
		return FOOD_NAMES.contains(name) || containsAny(name, FOOD_KEYWORDS);
	}

	private static boolean isMageGear(String name)
	{
		if (name.endsWith(" rune") || name.equals("runes"))
		{
			return true;
		}
		return containsAny(name, MAGE_KEYWORDS);
	}

	private static boolean isRangedGear(String name)
	{
		if (name.contains("crossbow"))
		{
			return true;
		}
		if (name.contains("bow") && !name.contains("bowl") && !name.contains("bow string")
			&& !name.contains("rainbow"))
		{
			return true;
		}
		if (name.contains("blowpipe") || name.contains("ballista") || name.contains("chinchompa"))
		{
			return true;
		}
		if (name.endsWith("arrow") || name.endsWith("arrows") || name.endsWith("bolts")
			|| name.endsWith(" bolt") || name.endsWith("javelin") || name.endsWith("javelins"))
		{
			return true;
		}
		if (name.endsWith(" dart") || name.endsWith(" darts")
			|| name.endsWith(" knife") || name.endsWith(" knives"))
		{
			return true;
		}
		return containsAny(name, RANGED_KEYWORDS);
	}

	private static boolean isJewellery(String name)
	{
		// Strip any charge suffix so "ring of dueling(8)" still reads as "... ring".
		String n = CHARGE_SUFFIX.matcher(name).replaceFirst("");
		return n.contains("amulet") || n.contains("necklace") || n.contains("bracelet")
			|| n.endsWith(" ring") || n.contains("ring of");
	}

	private static boolean isGem(String name)
	{
		return name.startsWith("uncut ") || GEM_NAMES.contains(name);
	}

	private static boolean containsAny(String name, Set<String> keywords)
	{
		for (String keyword : keywords)
		{
			if (name.contains(keyword))
			{
				return true;
			}
		}
		return false;
	}

	// --- Override parsing -----------------------------------------------------------

	private void ensureParsed()
	{
		final String raw = config.categoryOverrides();
		final String rawOrder = config.categoryOrder();
		if (Objects.equals(raw, cachedOverridesRaw) && Objects.equals(rawOrder, cachedOrderRaw))
		{
			return;
		}
		cachedOverridesRaw = raw;
		cachedOrderRaw = rawOrder;

		List<Override> parsed = new ArrayList<>();
		Set<String> customs = new LinkedHashSet<>();

		if (raw != null)
		{
			for (String line : raw.split("\\R"))
			{
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#"))
				{
					continue;
				}
				int eq = trimmed.indexOf('=');
				if (eq <= 0 || eq == trimmed.length() - 1)
				{
					continue;
				}
				String match = trimmed.substring(0, eq).trim();
				String section = canonicalSection(trimmed.substring(eq + 1).trim());
				if (match.isEmpty() || section.isEmpty())
				{
					continue;
				}

				Override override = parseOverride(match, section);
				if (override == null)
				{
					continue; // unparseable (e.g. invalid regex)
				}
				parsed.add(override);

				if (!BUILTIN_NAMES_LOWER.contains(section.toLowerCase(Locale.ROOT)))
				{
					customs.add(section);
				}
			}
		}

		overrides = parsed;
		customSectionOrder = new ArrayList<>(customs);
		userCategoryOrder = parseOrder(rawOrder);
	}

	/**
	 * Parses the user's preferred category order into canonical section names. Each line is
	 * matched (case-insensitively) against the built-in categories and the custom override
	 * sections; unrecognised or duplicate lines are dropped.
	 */
	private List<String> parseOrder(String raw)
	{
		List<String> result = new ArrayList<>();
		if (raw == null)
		{
			return result;
		}

		// lower-cased name -> canonical section name (built-ins + custom override sections)
		Map<String, String> known = new LinkedHashMap<>();
		for (Category c : Category.values())
		{
			known.put(c.getDisplayName().toLowerCase(Locale.ROOT), c.getDisplayName());
		}
		for (String custom : customSectionOrder)
		{
			known.put(custom.toLowerCase(Locale.ROOT), custom);
		}

		Set<String> seen = new LinkedHashSet<>();
		for (String line : raw.split("\\R"))
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#"))
			{
				continue;
			}
			String canonical = known.get(trimmed.toLowerCase(Locale.ROOT));
			if (canonical != null && seen.add(canonical))
			{
				result.add(canonical);
			}
		}
		return result;
	}

	/**
	 * Parses one override's match token into a rule. {@code /regex/} is a case-insensitive
	 * regular expression (partial match), a bare number is an exact item id, and anything
	 * else is a case-insensitive name substring. Returns null if the token can't be parsed
	 * (e.g. an invalid regex), so that line is ignored.
	 */
	private static Override parseOverride(String match, String section)
	{
		if (match.length() >= 2 && match.charAt(0) == '/' && match.charAt(match.length() - 1) == '/')
		{
			String body = match.substring(1, match.length() - 1);
			if (body.isEmpty())
			{
				return null;
			}
			try
			{
				return new Override(-1, null, Pattern.compile(body, Pattern.CASE_INSENSITIVE), section);
			}
			catch (PatternSyntaxException ex)
			{
				return null;
			}
		}
		if (ITEM_ID.matcher(match).matches())
		{
			return new Override(Integer.parseInt(match), null, null, section);
		}
		return new Override(-1, match.toLowerCase(Locale.ROOT), null, section);
	}

	private static String canonicalSection(String typed)
	{
		String lower = typed.toLowerCase(Locale.ROOT);
		for (Category c : Category.values())
		{
			if (c.getDisplayName().toLowerCase(Locale.ROOT).equals(lower))
			{
				return c.getDisplayName();
			}
		}
		return typed;
	}

	/** One override rule: an exact item id, a case-insensitive substring, or a regex. */
	private static final class Override
	{
		private final int itemId;        // -1 unless this is an id match
		private final String nameSubstr; // non-null only for a substring match
		private final Pattern regex;     // non-null only for a regex match
		private final String section;

		private Override(int itemId, String nameSubstr, Pattern regex, String section)
		{
			this.itemId = itemId;
			this.nameSubstr = nameSubstr;
			this.regex = regex;
			this.section = section;
		}

		private boolean matches(int canonicalId, String lowerName)
		{
			if (regex != null)
			{
				return regex.matcher(lowerName).find();
			}
			if (nameSubstr != null)
			{
				return lowerName.contains(nameSubstr);
			}
			return canonicalId == itemId;
		}
	}
}
