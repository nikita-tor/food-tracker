# Expansion ideas: feedback review

Feedback received 2026-10-18. Below are my notes on each request: what it involves, how much work it is, and what I'd recommend.

**Status (2026-10-18):** 4a, 1 and 3 are released in 0.1.4; 2a and 2b in 0.1.5. Decisions made: 100% juice counts fully as fruit; plain foods only, no mixed dishes; Open Food Facts only for packaged foods; the food list also stores carbs, fat, saturated fat, sugars, added sugars, sodium, potassium, calcium and iron for a possible 4b. 2a (calendar) and 2b (week/month charts, per-goal stats, streaks) make up the History tab: past days graded by current goals, days off not counted. Sodium and added-sugar limits (4b) are released in 0.1.6. Details are in `DEVELOPER.md`.

Lift scale: **S** ≈ half a day to a day, **M** ≈ 2–3 days, **L** ≈ a week or more. Each estimate includes tests, lint, hand-testing on the emulator, and keeping `DEVELOPER.md` up to date.

## Summary

| # | Request | Lift | New libraries | Saved-file format change | Recommendation | Status |
| --- | --- | --- | --- | --- | --- | --- |
| 4a | ~~Turn the existing goals (protein / fiber / fruit / veg) on or off~~ | **S** | None | One optional field | Do first. Cheap, and it decides how the other features display goals | ✅ Released in 0.1.4 |
| 1 | ~~Search common whole foods (banana, egg, oil)~~ | **M** | None (bundled data file) | None | Do. Biggest everyday improvement, and it can fill in fruit/veg servings automatically | ✅ Released in 0.1.4 |
| 2a | ~~Calendar view of goals met per day~~ | **M** | None | None for v1 | Do | ✅ Released in 0.1.5 |
| 2b | ~~Week/month graphs and consistency stats~~ | **M** | None (draw with Compose `Canvas`) | None | Do, right after 2a | ✅ Released in 0.1.5 |
| 3 | ~~Search branded products (Golden Curry, Gushers)~~ | **S–M** | None | None | Do, as a "search online" button rather than search-as-you-type | ✅ Released in 0.1.4 |
| 4b | Track new nutrients (sugar, sodium, carbs, iron…) | **L** | None | Yes, additive but broad | Wait. Find out exactly which nutrients are wanted first | ✅ Sodium and added-sugar limits released in 0.1.6 (see 4b) |

Suggested order: **4a → 1 → 3 → 2a → 2b**, with 4b kept for later. Features 1 and 3 belong in one search box on the Add food screen, so doing them back to back avoids redoing that screen. 4a goes first because the calendar's "goals met" count has to know which goals are turned on.

---

## 1. ~~Search common whole foods~~ ✅ Released in 0.1.4

**Idea:** typing "banana" on Add food shows "Banana, raw" with portion choices (1 medium, 1 cup sliced, 100 g). Picking one opens the existing food form, already filled in.

### Options

| | A. Bundled food list (recommended) | B. USDA FoodData Central API, online |
| --- | --- | --- |
| Data | A curated list of ~300–500 common foods, shipped inside the APK as `assets/foods.json` | Live search over all of USDA's data |
| Fruit/veg servings | **Yes.** USDA's Food Patterns Equivalents Database (FPED) gives cup-equivalents of fruit and vegetables per food, the same unit our goals use | No. The API returns nutrients only, so the user still types servings |
| Network / key | None. Works offline and instantly, so search-as-you-type is fine | Needs an api.data.gov key built into the APK (anyone with the APK can extract it). Limit is 1,000 requests/hour per key |
| Result quality | Clean, because we choose the names ("Egg, large") | Noisy ("Bananas, dehydrated, or banana powder", 40 kinds of egg) |
| License | Public domain (USDA) | Public domain |
| APK size | About +100–300 KB | None |

### How option A would work

- A one-off script (Python, in a new `tools/` folder, not part of the Gradle build) joins three public-domain USDA datasets: **FNDDS** (food names, nutrients, portion weights such as "1 medium banana = 118 g"), **FPED** (fruit/veg cup-equivalents per 100 g), and our curated list of which foods to include. It writes `app/src/main/assets/foods.json`. Re-run it when we want to add foods.
- `data/WholeFoods.kt`: loads the asset once and does the search. Search is pure Kotlin (word-prefix match, common foods ranked first), so it can be unit-tested like `Guidelines`.
- `AddFoodScreen`: under "My foods", a second section, "Common foods". Tapping one opens a portion picker, then `FoodFormScreen` with a pre-filled `FoodDraft`. The form, saving, and logging don't change.
- **No saved-file format change.** A picked food becomes an ordinary `FoodEntry` / `SavedFood`.

**Lift: M (~2–3 days).** About half of that is curating the list and checking it by hand (good names, sensible portions, spot-checking numbers against MyPlate). The app code is about 1 day.

**Decisions needed:**
- ~~**Fruit juice:** FPED counts 100% juice as fruit. The 2025–2030 guidelines push whole fruit. Count juice, count it at a discount, or leave it out?~~ **Decided:** juice counts fully.
- ~~**Beans and lentils:** USDA counts them as either a vegetable or a protein food. Probably count them as veg servings and also add their protein grams, since protein is tracked in grams anyway.~~ **Decided:** veg servings plus their protein grams.
- ~~**Simple mixed dishes** (rice and beans, chicken stir-fry): FNDDS has them. Include a handful of popular ones?~~ **Decided:** plain foods only for now.
- Note: vegetable oil has no protein, fiber, or fruit/veg, so logging it only changes anything when calories are shown. That's fine, but it can look like nothing happened.

---

## 2. Calendar view, then graphs

### 2a. ~~Calendar~~ ✅ Released in 0.1.5

- A new **History** tab (4th bottom tab, new `ic_calendar.xml` vector) or a calendar button on Today's date title. I lean toward the tab, since "how am I doing over time" is a separate question from "what did I eat today".
- A month grid. Each day shows how many goals were met (e.g. a small ring or a color from empty to citrus). Days with nothing logged are blank, **not** shown as failures. Tapping a day opens Today on that date (`FoodViewModel` needs a `goTo(date)` alongside `previousDay`/`nextDay`).
- A pure-Kotlin `History.kt` builds per-day summaries for a date range (group entries by date once, compare with the goals). It's unit-testable and fast enough: years of personal logs is a few thousand entries in memory.

**Decision needed: which goals do past days use?** Goals change when weight, age, or overrides change.
- **v1 (recommended):** use today's goals for every day. Simple, no format change. The catch: changing your weight re-grades old days. **Decided: this one.**
- **Later, if that bothers anyone:** save the goals for each day when settings change (a new optional `goalHistory` field, an allowed format change).

**Lift: M (~2 days).**

### 2b. ~~Week/month graphs~~ ✅ Released in 0.1.5

- On the History tab: a Week / Month toggle, a bar chart per goal (percent of goal each day, with the goal line at 100%), and simple stats: "Protein goal met 18 of 30 days", current streak, average.
- Drawn with Compose `Canvas` (~200 lines) to keep the no-libraries rule. Chart libraries exist, but simple bars aren't worth adding one for. Each chart gets a text description for screen readers.
- Hidden goals (4a) are left out of charts automatically.

**Lift: M (~2–3 days).**

Data-size note: `DEVELOPER.md` says to "move to Room if you need queries or history charts over large data". At personal scale (10 entries/day for 3 years ≈ 11k entries, ~2 MB of JSON) the single-file store is still fine. Keep an eye on it, but it isn't blocking.

---

## 3. ~~Search branded products~~ ✅ Released in 0.1.4

We already use Open Food Facts for barcodes, and it also has text search. I tried it today with the two examples from the feedback:

- **Classic search** (`/cgi/search.pl`) returned **HTTP 503 "temporarily unavailable"** on both tries. Its official limit is **10 searches per minute per user**, and the docs say explicitly not to use it for search-as-you-type.
- **Newer search service** (`search.openfoodfacts.org`) worked: "golden curry" found several S&B Golden Curry variants (Mild, Medium Hot…), and "gushers" found 60 products, mostly US. Results include the barcode but only partial nutrition (per 100 g, no serving size).

**Plan:** a "Search online for '…'" button under the local results (not search-as-you-type). Show the results; when the user picks one, look up its barcode with the existing `OpenFoodFacts.lookup()`, which already handles per-serving vs. per-100 g and opens the form. That's 1 search plus 1 product lookup per pick, well within the limits. A barcode that was saved before is still quick-added without a network call.

**Lift: S–M (~1–2 days).** Most of the code already exists (`OpenFoodFacts.kt`, the form, the "check against the label" wording).

**Caveats / decisions:**
- The data is crowd-sourced, and some products have missing numbers. The form already asks the user to check against the label.
- `search.openfoodfacts.org` is newer and less documented than the main API. If it proves unreliable, the fallback is **USDA FoodData Central "Branded Foods"**: much better US coverage, since the data comes from manufacturers' labels, but it needs the built-in API key from option 1B. For two people on sideloaded APKs that's acceptable, but it shouldn't go into a public build.
- Should also filter or rank results to US products (`countries_tags`), so imports from other countries don't crowd the list.

---

## 4. Choose what to track

### 4a. ~~Turn existing goals on/off (protein, fiber, fruit, veg)~~ ✅ Released in 0.1.4

- New optional `Settings.hiddenNutrients: Set<Nutrient>`, empty by default. Storing what's *hidden* rather than what's *tracked* means old files load with everything on, and any nutrient added later starts out on.
- Like calorie HIDE mode: **data is still recorded, only hidden**, so turning a goal back on brings its history back.
- Places that change: switches next to each goal in the Goals tab; Today's rings (layout for 1–3 rings instead of a fixed 4); the "2 of 4 goals met" summary; entry summaries in `Format.kt`; fields in the food form; later, calendar and charts. Keep at least one goal on, or the app has nothing to show.
- Test: an `AppDataJsonTest` case confirming older files load with nothing hidden.

**Lift: S (~1 day).**

### 4b. Track nutrients the app doesn't have yet

#### Plan (2026-10-18): daily limits for sodium and added sugars

Narrowed from "any nutrient" to two **limits**, things to stay *under*, as opposed to goals to reach. **Released in 0.1.6.** One change from this plan: the bar stays green near the limit with a "Close to the limit" note (from 90%) instead of turning amber, since amber/citrus already means "goal met".

**What the sources say** (checked against the documents on 2026-10-18):
- **Sodium:** Dietary Guidelines 2025–2030 (p. 5): "less than 2,300 mg per day" for ages 14+. Children: under 1,200 mg (ages 1–3), 1,500 mg (4–8), 1,800 mg (9–13). Pick by `Profile.age`, and use 2,300 mg when age isn't set.
- **Added sugars:** the 2025–2030 guidelines give no daily number ("no amount of added sugars… is recommended"; "one meal should contain no more than 10 grams of added sugars", p. 4). The daily limit uses the **FDA Daily Value, 50 g**, already in `Guidelines.SOURCES`.

**Decisions made:**
- Track **added sugars**, not total sugars, so fruit and plain milk don't count against you.
- Added-sugar limit: **50 g a day**. No per-meal flag.
- Limits are **off until switched on** in the Goals tab, so existing users see no change after updating.
- In History, limits are **kept separate** from the goal rings. They get their own chart cards ("under the limit on 9 of 12 days", bars past the line in red), and over-limit days get a small marker on the calendar. "Every goal met" stays about goals only.

**Design:**
- `Nutrients` gains `sodium: Double?` (mg) and `addedSugars: Double?` (g). Null means unknown, as with calories; unknown is not zero.
- Limits are their own kind (`Limit`, next to `Goal`), with guideline values and sources in `Guidelines.kt`, user overrides, and an on/off switch per limit.
- **Today:** a "Limits" section under the rings, with a bar per limit: "Sodium · 1,240 of 2,300 mg · 1,060 mg left". The bar turns amber from 75% and red when over ("over by 210 mg"), with no citrus reward. If some of the day's foods have no data: "2 foods have no sodium info".
- **Food form:** "Sodium (mg)" and "Added sugars (g)" fields, shown only when that limit is on (hidden fields keep their values, as with hidden goals).
- **Data:**
  - Common foods: `foods.json` already has `sodium` and `addedSugars`.
  - Open Food Facts: `sodium_serving`/`_100g` (in grams, ×1000 for mg) and `added-sugars_serving`/`_100g` when present.
- **Saved file:** optional `sodium`/`addedSugars` on each food and entry, plus limit settings. Older files load with unknown values and limits off. Add an `AppDataJsonTest` case for that.
- **Sources** shown in the Goals tab for each limit, and added to "Where the goals come from" in `DEVELOPER.md`.

**Lift: M–L (~3–4 days).** No full rework of how nutrients are stored, because only two fields are added.

#### Original notes

This is the large one, and it touches the app's whole design:

- `Nutrients` has four fixed fields. More nutrients means changing it to a map keyed by `Nutrient`. Every form, sum, format function, and the JSON encoder change. The JSON stays backward compatible (old keys are still read; new ones are just extra keys).
- **Each new goal needs a real government source**, per the project's rules: DRI tables by age and sex for micros, plus a "stay under" kind of goal for sodium, added sugar, and saturated fat. The rings currently mean "reach this"; "stay under" needs different visuals and different wording for "met".
- Barcode results can fill most of them (Open Food Facts has the fields). The whole-food list from feature 1 already stores carbs, fat, saturated fat, sugars, added sugars, sodium, potassium, calcium and iron. Anything else means adding columns to the script and rerunning it (FNDDS has 65 nutrients).
- **Product question:** carbs and fat as goals start to look like calorie counting, which the app deliberately avoids. Micros (iron, calcium, potassium, vitamin D) and limits (sodium, added sugar) fit the "track what matters" idea better. Worth asking which specific ones the person actually wants and why.

**Lift: L (~1–1.5 weeks)** for a handful of nutrients done properly with sources.

---

## Open questions to settle before building

1. ~~Do 4a, 1, 3, 2a, 2b in that order? Or do you want the History tab sooner?~~ **Decided:** that order.
2. ~~Whole foods (1): fruit juice counts as fruit, yes or no? Include a few mixed dishes?~~ **Decided:** juice counts; no mixed dishes.
3. ~~Branded search (3): start with Open Food Facts only, or plan for a USDA key from the start?~~ **Decided:** Open Food Facts only.
4. ~~History (2): grade past days by today's goals (simple) or by the goals at the time (needs goal history)?~~ **Decided:** today's goals.
5. ~~Extra nutrients (4b): which exact nutrients, if any?~~ **Decided:** daily limits for sodium and added sugars (plan under 4b).

## Sources

- Open Food Facts API rate limits (10 searches/min, 15 product reads/min, no search-as-you-type): <https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/>
- USDA FoodData Central API (key required; 1,000 requests/hour default; DEMO_KEY much lower): <https://fdc.nal.usda.gov/api-guide>
- USDA Food Patterns Equivalents Database (cup-equivalents of fruit/veg per food; public domain): <https://catalog.data.gov/dataset/food-patterns-equivalents-database-fped-8df33>
- Open Food Facts search results above: tested 2026-10-18 against `world.openfoodfacts.org/cgi/search.pl` and `search.openfoodfacts.org/search`.

---

## 5. Restaurant chain meals (feedback 2026-10-19) — ✅ Released in 0.1.8

"Add restaurant chain meal lookup, e.g. Five Guys cheeseburger, Triple Dipper." Built as a bundled, offline list (like common foods) from each chain's own published nutrition info: 9 chains, ~2,250 items, searched in the same box on Add food. No free, keyless API covers restaurants. Details, sources and the update steps are in "Restaurant menus" in `DEVELOPER.md`.

Open questions:
- Which other chains are wanted? McDonald's, Wendy's, Subway, Panera, Starbucks and Chipotle block scripted downloads, so they'd be typed in by hand from their PDFs (Chipotle and Subway are build-your-own, like Five Guys).
- Restaurants don't publish fruit/veg servings, so items start at 0 and the form asks the user to add them.

---

## 6. Not depending on "install over it, never uninstall" (plan, 2026-10-19)

**The problem:** everything lives in one file on the phone (`food_tracker.json`). Uninstalling, a new phone, or a build with a different signing key or app ID means that file is gone, so every release note says "don't uninstall".

### What already works: Android's own backup

The app has `allowBackup="true"` with empty backup rules, so Android's Auto Backup copies `food_tracker.json` into the user's Google Drive backup (a private area they don't see, 25 MB limit). Google's docs say it's restored on *any* install, `adb install` and sideloads included.

**Tested 2026-10-19 on the emulator** (with Android's local test transport, since the emulator has no Google account): backed up, uninstalled, reinstalled 0.1.9, and the name, log, and totals all came back.

Limits:
- Backups run about once a day, only when the phone is idle, on Wi-Fi, and has Google backup turned on (Settings → System → Backup). Up to a day of logging can be lost.
- The user can't see it, trigger it, or move the file somewhere else.
- It's expected to restore only into the same app ID with the same signing key (not tested).

### Options

| | What | Lift | Anything hosted? | Solves |
| --- | --- | --- | --- | --- |
| **A** ✅ built | **Backup file:** "Save a backup" and "Restore from a backup" in the Goals tab. Android's file picker saves the JSON wherever the user wants (Google Drive, Downloads, email). Restoring replaces the data after asking; older backups load like older files do now | **S** (~1 day) | No. No library and no network code: the file picker (Storage Access Framework) handles Drive | Reinstalls, new phones, and the app ID/key change in B. Manual, but visible and under the user's control |
| A+ | Like A, but automatic: pick a folder once (e.g. in Drive), and the app writes a backup there daily | S–M | No | Same as A, without remembering to do it |
| **B** | **Fix the app's identity, then use Google Play internal testing:** a real release keystore and an app ID like `com.<you>.foodtracker`; one last move via A; then share through Play's internal test track | **M** + $25 one-time Play account, identity verification | Google hosts the app | Updates come through the Play Store automatically, and no more "install unknown apps". Avoids Google's sideloading restrictions (see below). Play reinstalls also restore Auto Backup |
| C | **Our own cloud sync** (Firebase Auth + Firestore, or Supabase) | **L** (1–2+ weeks) | Yes, by us | Several devices, sharing. Adds sign-in, SDKs, a privacy policy, responsibility for health-ish data (weight, diet, notes), offline/conflict handling and ongoing upkeep, and breaks the "network only right after a tap" rule |
| C′ | **Sync into the user's own Google Drive** (Drive API app-data folder + Google sign-in) | M–L | No (the user's own Drive) | Like C without running anything, but needs a Google Cloud OAuth setup tied to the signing key, and a Google sign-in library |

### Why B matters soon

Google's developer verification started on 2026-09-30 in a few countries, with a global rollout planned for 2027 ([Android Developers Blog](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html), [Android Authority](https://www.androidauthority.com/google-android-advanced-flow-sideloading-rollout-begins-3700073/)). Apps from unverified developers will need an "advanced flow" to sideload: a restart and a 24-hour wait. `adb` installs are exempt. A Play-distributed app isn't affected. Re-check the details before acting: the rollout has changed several times.

Play notes: internal testing shares with up to 100 testers by email. The "12 testers for 14 days" closed-test rule for new personal accounts only applies before going to *production* (public), per third-party guides; check Google's Play Console Help.

### Recommendation

1. **A now.** Cheap, no hosting, and it's the safety net the other steps need.
2. **B before the 2027 sideloading changes**, using A for the one-time move to the new app ID.
3. **Skip C** unless several devices or sharing between people is actually wanted. If it is, prefer C′ (the user's own Drive) over hosting the data ourselves.

Once A and B are done, release notes can drop "don't uninstall": updates come from Play, and a reinstall restores from Auto Backup or a backup file.
