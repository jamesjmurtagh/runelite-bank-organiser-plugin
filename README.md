# Bank Assistant

A RuneLite plugin that adds a button to the bank which re-lays-out your items into
labelled category sections. The default sections are skill/combat-style based:
Melee Equipment, Ranged Equipment, Mage Equipment, Runecrafting, Mining & Smithing,
Woodcutting, Fishing, Farming, Herb Seeds, Tree Seeds, Herbs, Potions, Food, Teleports,
Jewellery, Gems, Currency, Equipment (other gear), Resources, and a final "Other".

It works exactly the way [Quest Helper](https://github.com/Zoinkwiz/quest-helper)
organises the bank when you click its "View tab quest-helper" button, but it is
quest-agnostic: sections come from automatic item categorisation rather than a
quest's item requirements.

## How it works

1. The category layout **replaces the bank's default all-items view** — open the bank
   and it's already organised, no click required.
2. On each bank rebuild, the plugin reuses the game's own item widgets, hides them,
   then redraws them grouped under category headers (8 items per row).
3. Withdraws, placeholders and examines all keep working — the menu's slot index is
   remapped to the item's real bank slot on click.
4. A small button (bank icon, left of where Quest Helper puts its button) toggles back
   to the vanilla layout and on again. It defaults to organised.

### Scope and coexistence

- Only the **all-items view** (`BANK_CURRENTTAB == 0`) is reorganised. Selecting an
  individual bank tab, or running a bank search, shows that view normally.
- It **steps aside whenever the bank is searching**. Quest Helper forces a searching
  state while its quest tab is active, so the two coexist automatically: QH's button
  still shows the quest items; otherwise you see categories. The plugin only *reads*
  the searching flag (it never forces it), and its button sits clear of QH's.

Categorisation is by skill/combat **style**, decided by an ordered first-match-wins
rule engine (`BankCategoriser.categorise`). Skill/style name rules run **before**
equipment slot: a pickaxe (a weapon-slot item) is filed under **Mining & Smithing**, a
mystic robe (a body-slot item) under **Mage Equipment**. Equipment slot is only the
**fallback** — residual weapons and body armour default to **Melee Equipment**, which is
why all plate gear lands there without a keyword for every tier. The keyword lists are
best-effort, not exhaustive; use the overrides below to correct anything they miss.

### Category overrides (no rebuild needed)

The plugin settings have a **Category overrides** box. Put one rule per line as
`match=Category`:

```
pickaxe=Mining
chisel=Tools
/\w* rune/=Runes
/^coal$/=Fuel
995=Currency
```

- `match` is part of an item name (case-insensitive substring), an exact item id, or a
  **`/regex/`** pattern (wrapped in slashes, case-insensitive, partial match). So
  `pickaxe=Mining` files every pickaxe under "Mining" — even though pickaxes are
  weapon-slot items that would otherwise be **Weapons** (overrides win over every
  built-in rule, including equipment slot) — and `/\w* rune/=Runes` files every
  "&lt;something&gt; rune" into a custom Runes section. Anchor with `^`/`$` for an exact
  name (e.g. `/^coal$/=Fuel`). Bare matches (no slashes) are always literal substrings,
  so item names containing regex characters like `Prayer potion(4)` still work.
- The category name can be a built-in one or a **new** one. New categories become new
  sections, drawn at the **top** of the bank in the order they first appear in the box.
- **Earlier lines win** when more than one rule matches an item.
- Lines that are blank or start with `#` are ignored. Edits apply as soon as the box
  loses focus, while the bank is open.

### Category order

The **Category order** setting controls the top-to-bottom order of the sections. List
section names one per line, in the order you want them shown; anything you don't list
keeps its default order below. Names are case-insensitive and match either a built-in
category or one of your custom override sections.

```
Food
Potions
Runecrafting
```

That pins Food, Potions, then Runecrafting to the top; every other category follows in
the default order. Unrecognised names are ignored.

### Loadouts (Quest-Helper-style placeholders)

The **Loadouts** setting lets you define named checklist sections shown at the **top** of
the bank — the exact items + quantities you need for a trip, as placeholders (greyed if
you're missing them) with a have/need count and a tick or cross, just like Quest Helper's
quest tab. Owned items are withdrawable; missing items show "Details" telling you what's
left. Crucially these are **duplicates** — they do not remove the items from their normal
categories.

Start each loadout with a `[Name]` header, then one item per line (optional quantity
prefix, default 1; name or `id:1234`):

```
[Herb Farming Run]
Ectophial
4 Law rune
14 Air rune
1 Earth rune
1 Water rune
Dramen staff
```

Item names are matched against the game's item list (case-insensitive, exact name).

### Import / export

The **Import / Export** settings section lets you move your whole setup (overrides,
loadouts, category order, sound) as a single string:

- **Export to clipboard** — flip this toggle and your current configuration is copied to
  the clipboard as a base64 string (the toggle flips back off). Paste it anywhere to share.
- **Import string** + **Load imported config** — paste a string into the box, then flip
  the load toggle to apply it. Invalid strings are rejected and change nothing.

## Building

Requires a **JDK 11** (RuneLite targets Java 11).

```sh
# Apple Silicon macOS: install via Homebrew (keg-only, no sudo)
brew install openjdk@11

# Build + run the unit tests. The gradlew launcher needs JAVA_HOME pointed at a JDK;
# compilation itself is pinned to JDK 11 via gradle.properties.
JAVA_HOME=/opt/homebrew/opt/openjdk@11 ./gradlew build
```

If your JDK 11 lives elsewhere, run `/usr/libexec/java_home -v 11` to find it and
update `org.gradle.java.home` in `gradle.properties`.

## Running it in a live client

`src/test/java/com/bankorganiser/BankOrganiserPluginTest` launches a RuneLite client
with the plugin loaded for manual testing:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@11 ./gradlew shadowJar
java -jar build/libs/bank-organiser-1.0.0-all.jar
```

Or run that `main` directly from IntelliJ IDEA (open the folder as a Gradle project).

## Project layout

| Path | Purpose |
| --- | --- |
| `BankOrganiserPlugin` | Plugin entry; registers the layout manager. |
| `BankOrganiserConfig` | Config panel toggles. |
| `banktab/BankOrganiserButton` | The toggle button (defaults to organised; coexists with QH). |
| `banktab/BankLayoutManager` | Re-lays-out the all-items view into sections on rebuild; yields while searching. |
| `banktab/BankSection` | A section: header + the item ids under it. |
| `category/Category` | The section list and their display order. |
| `category/BankCategoriser` | Maps an item id → a category (ordered rules). |

## Credit

The bank widget-manipulation technique is adapted from Quest Helper's
`com.questhelper.bank.banktab` package (BSD 2-Clause), © Zoinkwiz and contributors.
