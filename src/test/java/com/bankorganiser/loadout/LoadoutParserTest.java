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

import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoadoutParserTest
{
	private static final Map<String, Integer> ITEMS = Map.of(
		"ectophial", 4251,
		"law rune", 563,
		"air rune", 556,
		"earth rune", 557,
		"water rune", 555,
		"dramen staff", 772);

	private final ToIntFunction<String> resolver = name -> ITEMS.getOrDefault(name, -1);

	private List<Loadout> parse(String raw)
	{
		return LoadoutParser.parse(raw, resolver);
	}

	@Test
	public void parsesAHeaderAndItemsWithQuantities()
	{
		List<Loadout> loadouts = parse(
			"[Herb Farming Run]\n"
				+ "Ectophial\n"
				+ "4 Law rune\n"
				+ "14 Air rune\n"
				+ "1 Earth rune\n"
				+ "1 Water rune\n"
				+ "Dramen staff");

		assertEquals(1, loadouts.size());
		Loadout loadout = loadouts.get(0);
		assertEquals("Herb Farming Run", loadout.getName());
		assertEquals(6, loadout.getItems().size());

		LoadoutItem ectophial = loadout.getItems().get(0);
		assertEquals(4251, ectophial.getItemId());
		assertEquals(1, ectophial.getQuantity());

		LoadoutItem law = loadout.getItems().get(1);
		assertEquals(563, law.getItemId());
		assertEquals(4, law.getQuantity());

		assertEquals(14, loadout.getItems().get(2).getQuantity());
	}

	@Test
	public void supportsMultipleLoadouts()
	{
		List<Loadout> loadouts = parse("[A]\nLaw rune\n[B]\n2 Air rune");
		assertEquals(2, loadouts.size());
		assertEquals("A", loadouts.get(0).getName());
		assertEquals("B", loadouts.get(1).getName());
		assertEquals(556, loadouts.get(1).getItems().get(0).getItemId());
	}

	@Test
	public void resolvesItemIdSyntax()
	{
		List<Loadout> loadouts = parse("[X]\n3 id:995");
		assertEquals(995, loadouts.get(0).getItems().get(0).getItemId());
		assertEquals(3, loadouts.get(0).getItems().get(0).getQuantity());
	}

	@Test
	public void dropsUnresolvedItemsAndComments()
	{
		List<Loadout> loadouts = parse(
			"[X]\n"
				+ "# a comment\n"
				+ "Nonexistent item\n"
				+ "\n"
				+ "Law rune");
		assertEquals(1, loadouts.get(0).getItems().size());
		assertEquals(563, loadouts.get(0).getItems().get(0).getItemId());
	}

	@Test
	public void ignoresItemsBeforeAnyHeaderAndEmptyLoadouts()
	{
		// Items before a header are dropped; a header whose items all fail to resolve is dropped.
		List<Loadout> loadouts = parse("Law rune\n[Empty]\nNonexistent item");
		assertTrue(loadouts.isEmpty());
	}

	@Test
	public void blankConfigYieldsNoLoadouts()
	{
		assertTrue(parse("").isEmpty());
		assertTrue(parse(null).isEmpty());
	}
}
