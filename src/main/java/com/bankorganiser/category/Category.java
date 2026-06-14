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

import lombok.Getter;

/**
 * The set of sections the bank is organised into. Declaration order here is the
 * order sections are drawn in the bank (top to bottom). {@link #OTHER} is the
 * catch-all for anything no rule matched and is always shown last.
 *
 * <p>The combat sections are by <em>style</em> (Melee / Ranged / Mage) rather than by
 * equipment slot, and the skilling sections group an entire skill's gear, gathered
 * resources and consumables together.
 */
@Getter
public enum Category
{
	MELEE_EQUIPMENT("Melee Equipment"),
	RANGED_EQUIPMENT("Ranged Equipment"),
	MAGE_EQUIPMENT("Mage Equipment"),
	RUNECRAFTING("Runecrafting"),
	MINING_SMITHING("Mining & Smithing"),
	WOODCUTTING("Woodcutting"),
	FISHING("Fishing"),
	FARMING("Farming"),
	HERB_SEEDS("Herb Seeds"),
	TREE_SEEDS("Tree Seeds"),
	HERBS("Herbs"),
	POTIONS("Potions"),
	FOOD("Food"),
	TELEPORTS("Teleports"),
	JEWELLERY("Jewellery"),
	GEMS("Gems"),
	CURRENCY("Currency"),
	EQUIPMENT("Equipment"),
	RESOURCES("Resources"),
	OTHER("Other");

	/** Human-readable section header shown in the bank. */
	private final String displayName;

	Category(String displayName)
	{
		this.displayName = displayName;
	}
}
