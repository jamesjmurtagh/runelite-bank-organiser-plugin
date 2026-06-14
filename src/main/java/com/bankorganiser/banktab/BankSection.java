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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * One labelled section in the organised bank: a header plus the ordered list of
 * bank item ids that fall under it. Analogous to Quest Helper's {@code BankTabItems},
 * but every item here is one the player actually owns, so there are no placeholders.
 */
@Getter
public class BankSection
{
	private final String name;
	private final List<Integer> itemIds = new ArrayList<>();

	public BankSection(String name)
	{
		this.name = name;
	}

	public void add(int itemId)
	{
		itemIds.add(itemId);
	}

	public boolean isEmpty()
	{
		return itemIds.isEmpty();
	}
}
