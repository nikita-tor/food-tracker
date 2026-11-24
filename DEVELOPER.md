# Food Tracker — developer notes

An Android app for tracking what matters instead of calories: **protein, fiber, and servings of fruit and vegetables**, with daily goals taken from US government guidelines. Calories can be shown, hidden, or ignored entirely.

Built on top of the Android Studio "Greeting Card" template, which is why the project folder and package are still named `greetingcard`.

**Contents:** [Features](#features) · [Project layout](#project-layout) · [Architecture decisions](#architecture-decisions) · [Where the goals come from](#where-the-goals-come-from) · [Network usage](#network-usage) · [External services](#external-services-all-free) · [Common foods list](#common-foods-list) · [Restaurant menus](#restaurant-menus) · [Building & running](#building--running) · [Sharing the app](#sharing-the-app-sideloading) · [Known gaps](#known-gaps--ideas)

## Features

| Feature | Where |
| --- | --- |
| Welcome screen on first launch: asks for the user's name (shown until a name is saved) | `ui/WelcomeScreen.kt` |
| Today screen: greeting with a one-line summary ("2 of 4 goals met"), a row of small progress rings for the tracked goals (all in one card, so the meals start on the first screen), food grouped by meal (an empty meal is just its title row), previous/next day | `ui/TodayScreen.kt` |
| Goal rings fill up with an animation and turn citrus (ring and a filled center) when a goal is met. Under each: "38 g to go", "1 to go" for servings, or "Goal met" | `ui/Components.kt` (`GoalRing`), `toGoText` |
| "Repeat yesterday": on an empty meal, logs the same foods as that meal the day before (with Undo) | `FoodViewModel.repeatYesterday` |
| Daily notes: a short free-text note per day (up to 500 characters) at the bottom of Today. No button: it saves as you type, shows "Saved" for 2 seconds once written, and the keyboard's Done key closes it | `ui/TodayScreen.kt` (`NotesCard`) |
| Tap an entry to change servings, move it to another meal, or remove it (with Undo). Number boxes in dialogs open with the value selected, so typing replaces it | `ui/TodayScreen.kt` (`EditEntryDialog`), `NumberField(selectAllOnOpen)` |
| A short vibration confirms adding food (quick add, the food form, Repeat yesterday) | `HapticFeedbackType.Confirm` calls |
| Add food: a floating **Add to lunch** button on Today opens it for the meal that fits the time (`Meal.forHour`: breakfast 5–10, lunch 11–3, dinner 4–8, snacks otherwise); each meal's + opens it for that meal. Meal chips at the top switch the meal, with a line listing what's already in it. One search box covers saved foods, common foods, restaurants, and packaged foods online, with clear and scan-barcode icons in the box; "Enter a food yourself" and "Type a barcode" sit underneath. Tapping a saved food adds one serving and stays on the screen. After adding from the food form, you're back on Add food with the search still there, so several items in a row (three Triple Dipper picks) don't start over each time | `ui/AddFoodScreen.kt`, `FoodViewModel.openAddFood`, `addFoodQuery` |
| Common-foods search: ~320 whole foods (banana, egg, olive oil…) searched offline as you type. Picking one asks for a portion ("1 medium (200 g)", "1 cup", "100 g"), then opens the food form filled in, fruit/veg servings included | `data/WholeFoods.kt`, `assets/foods.json`, `tools/foods/` |
| Restaurant search: ~2,280 menu items from 10 chains (Applebee's, Chick-fil-A, Chili's, Culver's, Five Guys, IHOP, Jersey Mike's, Olive Garden, Taco Bell, Texas Roadhouse), offline, in the same search box. Chain words narrow the search ("five guys cheeseburger"); a chain's name alone lists its menu. Picking one opens the food form filled in, named "Five Guys Cheeseburger". Chili's Triple Dipper is listed as its picks ("Triple Dipper pick: Big Mouth Bites"), so you add three | `data/Restaurants.kt`, `assets/restaurants.json`, `tools/restaurants/` |
| Packaged-food search: a "Search online" button looks up Open Food Facts by name (US products first); picking a result runs the normal barcode lookup | `OpenFoodFacts.search`, `FoodViewModel.searchOnline` |
| Food form for new foods, barcode results, and editing saved foods | `ui/FoodFormScreen.kt` |
| "Save as meal": turns everything in one meal into a single quick-add item | `FoodViewModel.saveMealAsFavorite` |
| History tab: a Week / Month switch and arrows to move between periods. A calendar (one row for a week) where each day's ring fills by the share of goals met (citrus when all are); days with nothing logged have no ring. Tap a day to open it on Today | `ui/HistoryScreen.kt`, `data/History.kt` |
| History charts: per tracked goal, a bar per day against a dashed goal line (citrus once reached, capped at 150% so one big day doesn't flatten the rest), "met on X of Y days", the daily average, and the current streak. An every-goal streak shows under the period title | `GoalChartCard`, `History.stats`, `History.streak` |
| My foods tab: edit, delete, add saved foods and meals | `ui/SavedScreen.kt` |
| Daily limits for sodium and added sugars: things to stay *under*, off until switched on in the Goals tab. Today shows a bar per limit ("1,240 of 2,300 mg · 1,060 mg left", red with "Over by …" once over, a note near 90%) and says how many foods have no data for it. The food form gets Sodium (mg) and Added sugars (g) fields; common foods and barcode results fill them in. History gets a chart card per limit and a red dot on days over one | `Limit` in `data/Guidelines.kt`, `LimitsCard` in `ui/TodayScreen.kt`, `History.limitStats` |
| Backups: a ruby **Save a backup** button in the Goals tab writes everything to a JSON file wherever the user picks with Android's file picker (Google Drive, Downloads…); **Restore** reads one back, says what's in it, and asks before replacing everything (with Undo). The welcome screen has **Restore from a backup** for a new phone or a reinstall. "Last backup: Oct 3 (16 days ago)" under the button | `data/Backup.kt`, `ui/BackupUi.kt`, `FoodViewModel.saveBackup`/`openBackup` |
| Goals tab: name, calorie mode, sex/age/weight (lb or kg), goal overrides, a switch per goal, daily limits (switch and own number each), sources, and a footer with the app version and "© 2026 Nikita Torosyan" | `ui/SettingsScreen.kt` |
| Turning a goal off hides it everywhere (ring, summary, food lines, form field), but its numbers are still recorded, so turning it back on brings its history back. The last goal can't be turned off | `Settings.hiddenNutrients`, `FoodViewModel.setTracked` |

### Calorie modes (`CalorieMode` in `data/Models.kt`)

- **SHOW**: calories appear on entries, in the daily total, and in the form.
- **HIDE**: calories from barcodes are still stored, but are never displayed anywhere. Switching back to SHOW reveals them.
- **OFF**: calories are stripped before saving (`FoodViewModel.applyCalorieMode`), even from barcode results.

Calories are deliberately **not** a goal. There is no calorie target anywhere in the app.

## Project layout

```
app/src/main/java/com/example/greetingcard/
├── MainActivity.kt        App shell: bottom nav, back handling, snackbar, screen switching
├── FoodViewModel.kt       All state + actions; also the Screen back stack
├── Screen.kt              Screen (back stack entries) and FoodDraft (food form input)
├── data/
│   ├── Models.kt          Nutrients, FoodEntry, SavedFood, Settings, enums
│   ├── Guidelines.kt      Goal calculation from the government guidelines (pure Kotlin, unit-tested)
│   ├── History.kt         Per-day results, goal stats, streaks, week/month ranges (pure Kotlin, unit-tested)
│   ├── Store.kt           Load/save everything as one JSON file (format in AppDataJson)
│   ├── OpenFoodFacts.kt   Barcode → product lookup, and packaged-food text search
│   ├── WholeFoods.kt      Common-foods list: parsing and offline search
│   └── Restaurants.kt     Restaurant menus: parsing and offline search
└── ui/
    ├── Components.kt      Shared composables: GoalRing, SavedFoodRow, NumberField, SecondaryText, BackButton
    ├── Format.kt          Number/nutrient text formatting (no Compose)
    ├── WelcomeScreen.kt   First-launch name screen
    ├── TodayScreen.kt
    ├── HistoryScreen.kt
    ├── AddFoodScreen.kt
    ├── FoodFormScreen.kt
    ├── SavedScreen.kt
    └── SettingsScreen.kt
app/src/test/.../GuidelinesTest.kt   Unit tests for goals and nutrient math
app/src/test/.../AppDataJsonTest.kt  Saved-file format: round trip, and old files still loading
app/src/test/.../FormatTest.kt       Text formatting and the greeting
app/src/test/.../HistoryTest.kt      Day summaries, stats, streaks, ranges, and calendar layout
app/src/test/.../WholeFoodsTest.kt   Common-foods search ranking, portions, and checks on the real foods.json
app/src/test/.../OpenFoodFactsTest.kt  Parsing packaged-food search results
app/src/test/.../RestaurantsTest.kt  Restaurant search ranking, names, and checks on the real restaurants.json
app/src/main/assets/foods.json       Common-foods list, generated by tools/foods (don't edit by hand)
app/src/main/assets/restaurants.json Restaurant menus, generated by tools/restaurants (don't edit by hand)
app/src/main/res/drawable/ic_*.xml   Icons (hand-written vectors, no icon library)
tools/foods/                         Script and food list that generate foods.json (see "Common foods list")
tools/restaurants/                   Scripts and menu snapshots that generate restaurants.json (see "Restaurant menus")
tools/icon/                          Generator and checks for the launcher icon's foreground (see "App icon" above)
```

## Architecture decisions

The app is intentionally light on dependencies. The only library added on top of the template is Google's barcode scanner.

- **No Room/database.** All data (entries, saved foods, settings, notes) lives in one file, `filesDir/food_tracker.json`, written by `Store`. Writes go to a temp file and are then renamed. A Mutex keeps saves in order. If the file can't be parsed, it's renamed to `food_tracker.corrupt-<time>.json` rather than overwritten. This is fine for years of personal logs. Move to Room if you need queries or history charts over large data.
- **No Navigation library.** `FoodViewModel.backStack` is a list of `Screen` objects. `MainActivity` renders the top one, and `BackHandler` pops it. The bottom tabs reset the stack.
- **State** is plain Compose `mutableStateOf` inside an `AndroidViewModel`. Every change goes through `FoodViewModel.update {}`, which updates the state and saves it. Notes change on every keystroke, so their save waits until typing pauses for 600 ms (`update(saveDelayMs = ...)`). `MainActivity.onStop` writes any save still waiting, so leaving the app mid-sentence doesn't lose the note.
- **Saved-file format** (`AppDataJson` in `data/Store.kt`) only ever gains optional fields. Files from older versions load with defaults: no name (so the welcome screen shows once), no notes. Keep it that way, or existing users' data won't load after an update.
- **History** grades every past day against the goals as they are *now* (`vm.goals`: current weight, overrides, turned-off goals). Changing a goal re-grades old days. That keeps the saved file unchanged; storing goals per day would be an additive `goalHistory` field if it's ever needed. Weeks start on the phone's locale first day (Sunday in the US).
- **History stats only count logged days.** A day with no entries is a day off, not a failed day: it has no ring, no bar, and isn't in "met on X of Y days" or the average. Streaks count days in a row with the goal met up to today; today only joins once it's met, so a streak doesn't reset every morning, and a day off ends it. Streaks only show while the current week or month is on screen. Charts are plain Compose `Canvas` drawing, no chart library.
- **"Today" is refreshed when the app comes back to the front** (`MainActivity.onResume` → `FoodViewModel.onResume`). If the date changed while it was in the background, Today and History move to the new day, unless the user had picked another day on purpose. Screens read `vm.today`, not `LocalDate.now()`, so they redraw when it changes.
- **Per-day totals are cached** in `FoodViewModel.dailyTotals` and rebuilt only when the entries list changes (not on note keystrokes). Today and History both read from it.
- **Daily notes** are a map from ISO date to text (`AppData.notes`). Clearing a note removes its key. "Saved" is only shown once the file is actually written: `setNote` counts edits, and `saveNow` sets `noteSaved` only if no typing came in while that save ran (otherwise the next save will). Today shows it for 2 seconds after that moment, so returning to Today later doesn't show it again. The keyboard action is Done (`ImeAction.Done`), so notes are a single paragraph.
- **Limits are the opposite of goals**, and kept apart from them: `LimitNutrient` / `Limit` next to `Nutrient` / `Goal`. They're stored as `Settings.enabledLimits` (what's *on*, unlike goals), so older files and any limit added later start off. Their numbers live on `Nutrients` as `sodium` (mg) and `addedSugars` (g), both nullable: **null means unknown, not zero**, as with calories. Sums add what's known, and Today counts unknown foods separately ("2 foods have no sodium info"). In History, a day with no data for a limit isn't counted for it, and unknown is never "over". "Every goal met" and the rings ignore limits; over-limit days only get a red dot. Added sugars, not total sugars, so fruit and plain milk don't count against you. Near the limit, Today adds a note instead of turning amber, because citrus/amber already means "goal met".
- **Turned-off goals** are stored as `Settings.hiddenNutrients` (what's hidden, not what's tracked), so older files and any nutrient added later start out tracked. They work like calorie HIDE mode: the food form keeps the value a hidden nutrient started with instead of zeroing it.
- **The name** lives in `Settings.name`. `FoodViewModel.needsName` is true while it's blank, which is what shows the welcome screen. The Goals tab never saves a blank name, so you can't end up back on the welcome screen by clearing the field.
- **Snackbar messages** carry a unique `id`, so the same text twice still shows, plus an optional `undo` lambda.
- **No duplicate saved foods:** logging a food with "Save to my foods" on updates the saved food with the same name and serving label (case-insensitive; never a saved meal) instead of adding a copy: fresh numbers, use count +1. Up to 0.1.8, every search-and-log added another copy.
- **Backups** wrap the normal data-file JSON: `{"app":"FoodTracker","backupFormat":1,"createdAt":…,"appVersion":…,"data":{…}}`. Restore also accepts a raw `food_tracker.json`, and refuses anything else rather than loading it as empty data, since restoring replaces everything. Files go through Android's file picker (Storage Access Framework: `CreateDocument`/`OpenDocument`), so there's no storage permission, no library, and no network code: if the user picks Google Drive, the Drive app does the upload. The date of the last backup is stored as `settings.lastBackup` (optional, so older files load with "No backup yet"), and is written into the backup itself so a restored phone shows it. Android's Auto Backup still runs underneath (see "Network usage").
- **Nutrients** are stored *per serving*. An entry stores `servings`, and `FoodEntry.total` multiplies them. Saved meals store the summed totals as one "serving".
- **Look and feel:** the app always uses its own green palette (`ui/theme/Color.kt`), in light and dark versions. Wallpaper-based dynamic color is deliberately off, so the app looks the same on every phone and matches its icon. Cards use `MaterialTheme.colorScheme.card` (white in light mode). The citrus `tertiary` color means "goal met". `res/values*/colors.xml` and `themes.xml` give the launch screen the same background as the app, so there's no white flash in dark mode.
- **App icon:** `res/drawable/ic_launcher_foreground.xml` (a leaf in a three-quarters-full ring) on `ic_launcher_background.xml` (leaf green). The foreground is **generated** by `tools/icon/make_fg.py`: edit the values there and rerun it rather than editing the XML. Redesigned 2026-10-19 because it looked off-center: the ring was centered, but the gold arc puts the weight down-right, and the stem ran into the ring. The leaf is now symmetric, nudged (−1.6, −3.6) up-left, with the stem and tip 2.6+ units inside the ring, and the track is a little brighter (0.4). `tools/icon/centroid.py` measures the visual center (now 0.2% off, was 2.7%), and `tools/icon/render.py` draws a PNG preview with the launcher mask (`--guides` adds the safe zone and centerlines); both are plain Python. The foreground doubles as the themed/monochrome icon, so the vein is cut out of the leaf (`evenOdd`) rather than painted, or it would vanish in one color. `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml` point at them. The template's `mipmap-*dpi/*.webp` bitmaps were removed (2026-10-19): with minSdk 26 every phone uses the vector icon.
- **minSdk is 26** (raised from the template's 24) so `java.time.LocalDate` works without desugaring.

## Where the goals come from

All of this is in `data/Guidelines.kt`. Each goal shows its source to the user in the Goals tab.

| Goal | Rule | Source |
| --- | --- | --- |
| Protein | 1.2 g × body weight (kg), if weight is set | Dietary Guidelines for Americans 2025–2030 (range 1.2–1.6 g/kg; the app uses the low end) |
| Protein (no weight) | 46 g women / 56 g men / 50 g not set | RDA (0.8 g/kg) / FDA Daily Value |
| Fiber | Women 25 g (19–50), 21 g (51+), 26 g (9–18); men 38 g (14–50), 30 g (51+), 31 g (9–13); 28 g if sex not set | National Academies Adequate Intake (~14 g per 1,000 kcal) / FDA Daily Value |
| Fruit | 2 servings | DGA 2025–2030, 2,000-calorie pattern |
| Vegetables | 3 servings | DGA 2025–2030, 2,000-calorie pattern |

One serving ≈ 1 cup of fruit or vegetables, 2 cups of leafy greens, or ½ cup of dried fruit. Users can override any goal; overrides are kept in `Settings.customGoals`.

**Daily limits** (off by default), in `Guidelines.limits`:

| Limit | Rule | Source |
| --- | --- | --- |
| Sodium | Less than 2,300 mg from age 14 (also when age isn't set); 1,800 mg ages 9–13, 1,500 mg ages 4–8, 1,200 mg ages 1–3 | DGA 2025–2030, p. 5 |
| Added sugars | 50 g | FDA Daily Value. The DGA 2025–2030 gives no daily number: "no amount of added sugars… is recommended", and "one meal should contain no more than 10 grams" (p. 4). We chose not to show a per-meal flag |

Users can override either limit; overrides are kept in `Settings.customLimits`.

#### Source documents

The Goals tab links to these too; the list lives in `Guidelines.SOURCES`, so update both together. Checked against the documents on 2026-10-18.

| Source | Link | What it backs up |
| --- | --- | --- |
| Dietary Guidelines for Americans 2025–2030 (USDA & HHS, released Jan 2026) | <https://cdn.realfood.gov/DGA.pdf> (linked from dietaryguidelines.gov and realfood.gov) | Protein 1.2–1.6 g/kg/day (p. 2); 3 vegetable and 2 fruit servings/day on a 2,000-calorie pattern (p. 3) |
| National Academies, *DRI: The Essential Guide to Nutrient Requirements*, Fiber chapter | <https://nap.nationalacademies.org/read/11537/chapter/11> (full report: <https://nap.nationalacademies.org/catalog/10490>) | Fiber Adequate Intakes by age and sex, 14 g per 1,000 kcal |
| FDA Daily Values | <https://www.fda.gov/food/nutrition-facts-label/daily-value-nutrition-and-supplement-facts-labels> | Fiber 28 g and protein 50 g, the fallbacks when sex or weight isn't set |
| USDA MyPlate, Fruit and Vegetable groups | <https://www.myplate.gov/eat-healthy/fruits>, <https://www.myplate.gov/eat-healthy/vegetables> | What counts as one serving (cup-equivalent). The DGA 2025–2030 says "servings" without defining them, so the app uses USDA's cup-equivalents |

The 46 g / 56 g protein fallbacks are the RDA of 0.8 g/kg applied to reference body weights (Essential Guide, Protein chapter).

## Network usage

**In short:** the app makes exactly two kinds of web request, both to Open Food Facts, and only right after the user taps something. Nothing runs at startup, in the background, or on a timer. There's no analytics, crash reporting, ads, account, or sync. All HTTP code is in `data/OpenFoodFacts.kt`, and the only network permission is `INTERNET` in the manifest.

### Requests the app makes

| | Request | Happens when | What's sent | What comes back |
| --- | --- | --- | --- | --- |
| 1 | **Product lookup**<br>`GET https://world.openfoodfacts.org/api/v2/product/<barcode>.json?fields=product_name,brands,serving_size,nutriments`<br>(`OpenFoodFacts.lookup`) | Add food: a barcode is scanned, **or** a typed barcode is looked up, **or** a result from the online search (2) is tapped. **Skipped** when the barcode belongs to a saved food: that one is quick-added without a request (`FoodViewModel.onBarcode`) | The barcode digits | Name, brand, serving size, nutrient values. Opens the food form. 404 → "No product found", and an empty form with the barcode |
| 2 | **Packaged-food search**<br>`GET https://search.openfoodfacts.org/search?q=<text>&page_size=25&fields=code,product_name,brands,quantity,countries_tags`<br>(`OpenFoodFacts.search`) | Add food: the **"Search online for …"** button (or **"Try again"** after a failure). **Never while typing**: the service doesn't allow search-as-you-type | The text in the search box | Up to 25 products: barcode, name, brand, package size, countries. No usable nutrition, so picking one triggers request 1 |

Both requests:
- Use HTTPS through `HttpURLConnection`, with a 10-second connect timeout and a 10-second read timeout.
- Send the header `User-Agent: FoodTracker-Android/<versionName>`, as Open Food Facts asks. No API key, cookies, or account.
- Send nothing about the user: no name, goals, log, or device ID. Only the barcode or the search text.
- Aren't cached and aren't retried automatically. A failure shows its reason (snackbar for lookups, inline with "Try again" for searches).

**Requests per action:**

| Action | Requests |
| --- | --- |
| Quick-add a saved food or meal, or add a common food (banana, eggs…) or restaurant item | 0 |
| Scan or type a barcode that's already saved | 0 |
| Scan or type a new barcode | 1 (lookup) |
| Search online, then pick a result | 2 (search + lookup) |
| Search online, then pick nothing | 1 (search) |

Limits are in [External services](#external-services-all-free): 15 lookups a minute per user. Normal use stays far below that.

### Never uses the network

- **Common-foods search:** the list is bundled in the APK (`assets/foods.json`) and searched on the phone.
- **Backups:** saved and opened through Android's file picker. If the user picks Google Drive (or another cloud folder), that app uploads or downloads the file, not this one.
- **Restaurant search:** the menus are bundled in the APK (`assets/restaurants.json`) and searched on the phone. Chains' websites are only contacted by the developer's `tools/restaurants/fetch_menus.py`, never by the app.
- **Saved foods, Today, History and charts, goals, notes, settings:** all local.
- **Saving:** everything goes to one file on the phone, `filesDir/food_tracker.json`.

### Network use by Android, not the app

| What | When | Notes |
| --- | --- | --- |
| Google code scanner module | Downloaded by Google Play services at install time (manifest `meta-data` `barcode_ui`), or on the first scan if that didn't happen | Google Play services does this, not the app's code. According to Google, decoding runs on the device, and the app only receives the barcode number. |
| Android Auto Backup | Whenever Android runs its backup, if the user has Google backup turned on | `android:allowBackup="true"`, and the template's `backup_rules.xml` and `data_extraction_rules.xml` are still empty, so **`food_tracker.json` (name, profile, log, notes) is included in the user's Google account backup**. That's good for not losing data. If it's ever unwanted, exclude the file in both XML files. |
| Links in the Goals tab | When the user taps a source link | Opens the phone's browser. The app itself makes no request. |

**Adding a network call?** Keep it in `data/OpenFoodFacts.kt`, or a file next to it, and add it to the tables above.

## External services (all free)

Rate limits, licenses, and quirks of each service. For which requests are made and when, see [Network usage](#network-usage).

| Service | Used for | Cost / limits |
| --- | --- | --- |
| **Open Food Facts** API (`world.openfoodfacts.org/api/v2/product/<barcode>.json`) | Barcode → name, serving size, protein, fiber, calories | Free, no API key, no account. Limit: **15 product lookups per minute**, counted per user/phone for mobile apps. Data is ODbL-licensed, so attribution is required; the Goals tab credits it. |
| **Open Food Facts search** (`search.openfoodfacts.org/search?q=…`) | Packaged foods by name → barcodes, then the lookup above | Free, no key. Newer and less documented than the main API. The main API's own search (`/cgi/search.pl`, 10 per minute) returned 503 errors when tested on 2026-10-18. Neither may be used for search-as-you-type, so the app only searches when the button is pressed. Results have the barcode but not usable nutrition, so each pick costs one search plus one product lookup. |
| **Google code scanner** (`play-services-code-scanner`) | Camera barcode scanning UI | Free, runs on-device, no API key. Needs Google Play services. The scanner module is downloaded at install time (manifest `meta-data`). |

Notes:
- Open Food Facts asks for a User-Agent in the form `AppName/Version (ContactEmail)`. The app sends `FoodTracker-Android/<versionName>` (from `BuildConfig.VERSION_NAME`) with no contact. Add a contact email in `OpenFoodFacts.kt` if this goes beyond personal use.
- Open Food Facts doesn't provide fruit/veg servings, so the user enters those. A product not in the database opens an empty form with the barcode attached. If the barcode was saved before, it's quick-added without a network call.
- Coverage of US products is good but not complete. Values are crowd-sourced, which is why the form says to check them against the label.
- If `search.openfoodfacts.org` goes away or becomes unreliable, the fallback is USDA FoodData Central's Branded Foods API. It has better US coverage, but needs an api.data.gov key built into the APK (see `Expansion.MD`).

## Common foods list

`app/src/main/assets/foods.json` holds ~320 common whole foods for offline search. It's **generated**: change `tools/foods/whole_foods.tsv` and rerun the script rather than editing the JSON.

- **What's in it:** per 100 g, protein, fiber, calories, fruit/veg servings, sodium and added sugars (what the app uses), plus carbs, fat, saturated fat, total sugars, potassium, calcium, and iron, which the app doesn't use yet. Added sugars come from FPED (teaspoon-equivalents × 4.2 g). Each food also has its common portions with gram weights.
- **Sources (public domain):** USDA **FNDDS 2019–2020** for nutrients and portions ([FoodData Central downloads](https://fdc.nal.usda.gov/download-datasets), "Survey (FNDDS)" JSON, `FoodData_Central_survey_food_json_2022-10-28.zip`), and USDA **FPED 2017–2020** for fruit/veg cup-equivalents ([FPED databases](https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/food-surveys-research-group/docs/fped-databases/), `FPED_1720.xls`). Keep the two versions matched, since FPED is keyed by FNDDS food code.
- **Rules built into the script:** fruit is FPED `F_TOTAL`, which includes 100% juice (counted fully, on purpose). Veg is `V_TOTAL + V_LEGUMES`, because the Dietary Guidelines count beans, peas, and lentils as vegetables and FPED keeps them separate. FPED's cup-equivalents already handle leafy greens (2 cups = 1 serving) and dried fruit (½ cup = 1 serving). Portions leave out survey-only entries ("Quantity not specified", "Guideline amount…"), and drinks get a "1 cup (8 fl oz)" portion.
- **Adding a food:** find its FNDDS food code (`foodCode` in the JSON download, or the "Survey (FNDDS)" item on FoodData Central). Add a line to `whole_foods.tsv`: code, display name starting with the word people search for, and optional `|`-separated aliases. Then rerun:
  ```sh
  pip install xlrd   # once, to read the .xls
  python tools/foods/build_food_list.py <path>/FoodData_Central_survey_food_json_2022-10-28.json <path>/FPED_1720.xls
  ./gradlew :app:testDebugUnitTest --tests "com.example.greetingcard.data.WholeFoodsTest"
  ```
  The script stops on unknown codes or duplicate names. `WholeFoodsTest` checks that the real file loads and that common searches ("banana", "eggs", "oj") find the right food.

## Restaurant menus

`app/src/main/assets/restaurants.json` holds ~2,280 menu items from 10 chains, searched offline (`data/Restaurants.kt`). It's **generated**: refetch or edit `tools/restaurants/menus/*.tsv` and rerun the build script rather than editing the JSON.

- **Why bundled:** no free, keyless API covers chain restaurants (checked 2026-10-19). Open Food Facts has almost nothing for them; NYC's MenuStat stopped in 2018 and has no totals for built items; Nutritionix and FatSecret need an API key (and FatSecret an allow-listed server IP), which a sideloaded APK can't keep secret. A bundled list also works offline and instantly, like common foods.
- **Sources:** only each chain's **own published nutrition info**, the page its website links to (listed in `tools/restaurants/chains.tsv` and shown in the food form's note):

  | Chain | Source | How it's read |
  | --- | --- | --- |
  | Applebee's, Chili's, Culver's, IHOP, Taco Bell, Texas Roadhouse | The chain's "Interactive Nutrition Menu", hosted by Nutritionix and linked from the chain's site | `nutritionix.com/<slug>/menu/premium` table. (The older `nix-vue-inm.s3.amazonaws.com/restaurant/<slug>/data/menu-latest.json.gz` files are stale for some chains, e.g. Taco Bell's is from 2025.) |
  | Chick-fil-A | chick-fil-a.com/nutrition-allergens | The JSON behind the page's table. Sizes and options are listed separately |
  | Jersey Mike's | "Master Menu Label Report" PDF linked from jerseymikes.com/menu/nutrition (on `assets.ctfassets.net`, their content host) | `pdftotext -layout`. **Regular size on white bread only** (Mini, Giant, other breads, wraps and tubs left out); cold subs are as listed, "Mike's Way". The PDF names cold subs by number only, so menu names are in `JERSEY_MIKES_NAMES` in `fetch_menus.py`, taken from the chain's own calculator; a new number stops the fetch until it's added. The site itself blocks scripts (DataDome), but the PDF downloads fine. The PDF lacks some subs (#19 BBQ Beef, #20 Reuben, Chipotle Turkey, the clubs, Teriyaki, halal versions); they're left out rather than added up from ingredients |
  | Olive Garden | `media.olivegarden.com/en_us/pdf/olive_garden_nutrition.pdf` | `pdftotext -layout` (comes with Git for Windows); "add …" topping rows are left out |
  | Five Guys | US Nutrition & Allergen Guide PDF (September 2026) | **Typed in by hand**: Five Guys lists ingredients only, so burgers are added up (bun + 2 patties + a cheese slice per patty + bacon). This matches the protein Five Guys published per burger in older guides (e.g. Cheeseburger 47 g) |

  Only Nutritionix pages that a chain links to itself are used. Chains that Nutritionix covers on its own are their database product, not the chain's published info.
- **What's stored per item:** calories, protein, fiber, sodium, and added sugars when the chain publishes them (Taco Bell does; most only list total sugars, so added sugars stay unknown rather than 0). Values are as published (FDA-rounded); "<1 g" is stored as 0. **Fruit/veg servings are 0**: no chain publishes them, and the form's note asks the user to add them. Alcohol and catering/party trays are left out. An item listed under two sections with different numbers (Olive Garden lunch vs. dinner portions) keeps both, named with the section.
- **Not included (yet):** McDonald's, Wendy's, Subway, Panera, Starbucks and Chipotle. Their sites block scripted downloads or load data only in the browser. To add one by hand, write `menus/<id>.tsv` in the same format (first line: source and `YYYY-MM-DD`) with `manual` as the source in `chains.tsv`.
- **Updating** (menus change; refetch every few months):
  ```sh
  python tools/restaurants/fetch_menus.py            # all chains except manual ones; or name chain ids
  python tools/restaurants/build_restaurant_list.py  # checks every row, writes the asset
  ./gradlew :app:testDebugUnitTest --tests "com.example.greetingcard.data.RestaurantsTest"
  ```
  Review the `menus/*.tsv` diff before committing. The build stops on missing numbers, duplicate names, or protein that doesn't fit the calories (a sign of columns read in the wrong order). Five Guys has to be updated by hand from its PDF.

## Building & running

### Setup

- **Android Studio** (or just the Android SDK). The SDK path goes in `local.properties` (`sdk.dir=...`). Android Studio creates this file; it's machine-specific, so don't share it.
- **JDK:** nothing to install. The Gradle daemon runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`), and Gradle downloads it into `~/.gradle/jdks` the first time if it's missing.
- **Windows:** use `./gradlew` from Git Bash or `gradlew.bat` from cmd/PowerShell. They're the same thing.
- Toolchain: Gradle 9.5, AGP 9.3 (built-in Kotlin), Kotlin 2.2, Compose BOM 2026.02, compileSdk/targetSdk 37, minSdk 26. Versions live in `gradle/libs.versions.toml`.

### Common commands

| Command | What it does |
| --- | --- |
| `./gradlew :app:assembleDebug` | Debug APK → `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew :app:assembleRelease` | Release APK → `app/build/outputs/apk/release/app-release.apk` (see [Sharing the app](#sharing-the-app-sideloading)) |
| `./gradlew :app:installDebug` | Build and install on the connected phone/emulator |
| `./gradlew :app:testDebugUnitTest` | Unit tests (`app/src/test`). Report: `app/build/reports/tests/` |
| `./gradlew spotlessApply` | Auto-format all Kotlin and Gradle files |
| `./gradlew check` | **Everything:** ktlint, Android Lint, and unit tests. Run this before sharing a build |

Or just press Run in Android Studio.

**Before you call a change done:** run `./gradlew spotlessApply check`. The first part fixes formatting; the second fails if anything else is wrong.

### Linting

Two linters, both part of `./gradlew check`:

| Tool | Catches | Check | Report |
| --- | --- | --- | --- |
| **ktlint** (via Spotless) + [Compose rules](https://mrmans0n.github.io/compose-rules/) | Formatting, naming, import order, line length, Compose mistakes (missing `modifier` parameter, multiple root elements, passing the ViewModel too deep) | `./gradlew spotlessCheck` | Printed in the terminal, with file and line |
| **Android Lint** | Android-specific problems: unused resources, manifest mistakes, API level issues, accessibility, performance | `./gradlew :app:lint` | `app/build/reports/lint-results-debug.html` |

**Fixing a ktlint failure:** run `./gradlew spotlessApply` first. It fixes formatting by itself. Whatever it can't fix is listed as `file.kt:L<line> ktlint(<rule-id>) <message>`, and those need a manual fix. The rule id tells you what to search for in the [ktlint](https://pinterest.github.io/ktlint/latest/rules/standard/) or [Compose rules](https://mrmans0n.github.io/compose-rules/rules/) docs.

**Fixing an Android Lint failure:** open the HTML report. Each issue has an explanation and usually a suggested fix. Every warning fails the build (`warningsAsErrors` in `app/build.gradle.kts`), so new issues can't pile up.

**Rule configuration**

- ktlint settings are in `.editorconfig`. `no-unused-imports` is switched on there explicitly: with this code style it was off, and unused imports piled up while `check` stayed green (found and cleaned up 2026-10-19). After changing `.editorconfig`, run `./gradlew --stop` first, since the daemon keeps the old settings. Other settings: Android Studio code style, 120-character lines, trailing commas, LF line endings. Android Studio reads the same file, so **Code → Reformat Code** (Ctrl+Alt+L) mostly agrees with ktlint. When they disagree, `spotlessApply` wins.
- Screen-level composables (`*Screen`, `TabScaffold`, `TodayTopBar`) take the `FoodViewModel` directly. Everything below them takes plain values and callbacks. The `vm-forwarding-check` rule enforces this, with those screens allowlisted in `.editorconfig` (`compose_allowed_forwarding`). Add new screens to that list, or better, give them plain parameters.
- Android Lint settings are in the `lint { }` block of `app/build.gradle.kts`. The "newer dependency version available" checks are turned off: upgrade libraries on purpose, not because lint noticed a release.
- Linter versions are pinned in `gradle/libs.versions.toml` (`spotless`, `ktlint`, `composeRules`). After upgrading one, run `./gradlew spotlessApply` because new versions sometimes reformat code.

**Suppressing a warning** (only when the rule is genuinely wrong for that spot, with a comment saying why):

```kotlin
@Suppress("ktlint:compose:modifier-missing-check") // ktlint rule, by its id
@SuppressLint("UnusedResources")                    // Android Lint, by issue id
```

In XML resources and the manifest, use `tools:ignore="IssueId"` for Android Lint.

**No lint baseline:** Android Lint currently reports no issues at all, so nothing is being ignored. Keep it that way by fixing new warnings as they come up. If you ever inherit a batch of issues you can't fix right away, `./gradlew :app:updateLintBaseline` (plus `baseline = file("lint-baseline.xml")` in the `lint { }` block) records them so only new ones fail. Don't use a baseline to hide new problems.

### Troubleshooting

- **Odd Gradle behavior after changing build files or `.editorconfig`:** run `./gradlew --stop` to restart the daemon, then try again. The configuration cache (`org.gradle.configuration-cache=true` in `gradle.properties`) usually notices changes on its own.
- **`Unable to strip the following libraries ... libandroidx.graphics.path.so`** during a build is harmless. Gradle packages the library without stripping it.
- **Whole files show as reformatted with no visible change:** line endings. The project uses LF everywhere, and Spotless enforces it. Make sure your editor isn't converting files to CRLF.

### Emulator & debugging

**Emulator gotcha:** on this PC the `Pixel_6` emulator returns black screenshots with the default GPU mode. Start it with `emulator -avd Pixel_6 -gpu swiftshader_indirect` if you need screenshots or `adb` UI dumps. The first launch under software rendering takes several seconds. The emulator and `adb` are in `%LOCALAPPDATA%\Android\Sdk\emulator` and `...\Sdk\platform-tools`.

To inspect the saved data on a debug/emulator install:
```sh
adb shell run-as com.example.greetingcard cat files/food_tracker.json
```
(`run-as` only works on debuggable builds.)

To see a crash: `adb logcat -b crash` or `adb logcat | grep greetingcard`.

## Sharing the app (sideloading)

`dist/FoodTracker.apk` is a ready-to-install release build (currently **0.1.13**).

### Making a new release

1. Bump `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`. Android doesn't strictly require it for sideloading, but it lets you tell versions apart (Settings → Apps → Food Tracker shows the version).
2. Build and check, then copy the APK into `dist/`:
   ```sh
   ./gradlew spotlessApply check :app:assembleRelease
   cp app/build/outputs/apk/release/app-release.apk dist/FoodTracker.apk
   ```
3. Optional but cheap: confirm it's still signed with the same key as the copy people already have. If the SHA-256 lines differ, the update won't install over the old app.
   ```sh
   apksigner verify --print-certs dist/FoodTracker.apk   # apksigner is in Android/Sdk/build-tools/<version>/
   ```
4. Add a line to the release history below, and update `ROOMMATE.md` if anything visible changed.

If a release changes the saved-file format, test the update path before sharing: install the previous `dist/FoodTracker.apk` on an emulator, add some data, then `adb install -r` the new APK and check the data is still there.

### Release history

| Version | What changed |
| --- | --- |
| 1.0 (versionCode 1) | First shared version: goals, meals, saved foods, barcode lookup, calorie modes |
| 1.1 (versionCode 2) | Welcome screen asking for a name (shown once after updating), greeting on Today, a daily notes box, layout and spacing fixes |
| 0.1.2 (versionCode 3) | Renumbered to 0.x to reflect pre-release status (versionCode keeps increasing, so updates still install). Real app icon, the app's own green look in light and dark (no white flash on launch), animated rings that turn citrus when a goal is met, a "2 of 4 goals met" summary, "Repeat yesterday" on empty meals, number boxes that select their value, a light vibration when adding food |
| 0.1.3 (versionCode 4) | Goals tab sources are tappable links to the official documents (Dietary Guidelines 2025–2030, National Academies fiber intakes, FDA Daily Values, MyPlate serving sizes, Open Food Facts) |
| 0.1.4 (versionCode 5) | Food search: ~320 common foods (USDA, offline, fills in fruit/veg servings) and an online search for packaged foods (Open Food Facts) in one box on Add food. Goals can be switched off in the Goals tab (still recorded, just hidden). Saved-file format gained `settings.hiddenNutrients`; update from 0.1.3 tested on the emulator |
| 0.1.5 (versionCode 6) | Version and "© 2026 Nikita Torosyan" footer at the bottom of the Goals tab. History tab: Week / Month switch, a calendar of day rings (citrus when every goal is met, blank on days off), and per-goal bar charts with days met, daily average and streaks. Fix: if the app sat in the background past midnight, Today now moves to the new day instead of logging to yesterday. Fix: going back during a barcode lookup no longer pops the form up on another screen. No saved-file format change; update from 0.1.4 tested on the emulator |
| 0.1.6 (versionCode 7) | Daily limits for sodium (2,300 mg, less for children by age) and added sugars (50 g), off until switched on in the Goals tab: a bar per limit on Today, Sodium/Added sugars fields in the food form (filled in by common foods and barcodes), and a chart card per limit plus a red dot on over-limit days in History. Fix: the keyboard no longer covers the bottom of the food form or the Add food results. Saved-file format gained optional `sodium`/`addedSugars` and limit settings; update from 0.1.5 tested on the emulator (limits start off, older foods show as no data) |
| 0.1.7 (versionCode 8) | Notes: a brief "Saved" next to the character count once a note is written to the phone, and the keyboard's Enter key is now **Done**, which closes it (notes no longer take line breaks; older notes keep theirs). No saved-file format change; update from 0.1.6 tested on the emulator |
| 0.1.8 (versionCode 9) | Restaurant search: ~2,250 menu items from 9 chains (Applebee's, Chick-fil-A, Chili's, Culver's, Five Guys, IHOP, Olive Garden, Taco Bell, Texas Roadhouse) in the Add food search box, offline, from each chain's own nutrition info. Chili's Triple Dipper is listed as its individual picks. No saved-file format change; the release APK installed over an existing emulator install and kept its data (same signing key) |
| 0.1.9 (versionCode 10) | Easier logging: an **Add to …** button on Today that picks the meal by time of day; goal rings in one compact row so all meals fit on the first screen; Add food has meal chips, search at the top (clear and scan icons in the box), and returns to itself with the search kept after adding, for several items in a row; tap a logged food to move it to another meal; logging a food again updates its saved copy instead of duplicating it. No saved-file format change; update from the 0.1.8 release APK tested on the emulator |
| 0.1.10 (versionCode 11) | Backups: a ruby **Save a backup** button in the Goals tab saves everything to a file (Google Drive, Downloads…) through Android's file picker; **Restore** in Goals and **Restore from a backup** on the welcome screen bring it back, with a summary, a confirmation and Undo. "Last backup" reminder under the button. Saved-file format gained optional `settings.lastBackup`; update from the 0.1.9 release APK tested on the emulator (data kept, "No backup yet" shown), and backup → uninstall → reinstall → restore tested |
| 0.1.11 (versionCode 12) | Code cleanup, no visible changes: removed unused imports and the template's unused launcher bitmaps, and switched on ktlint's unused-import check so they can't pile up again. No saved-file format change; update from the 0.1.10 release APK tested on the emulator |
| 0.1.12 (versionCode 13) | Redrawn app icon: it looked off-center because the gold arc weighs it down-right (visual center 2.7% off, now 0.2%), and the stem ran into the ring. Rounder leaf nudged up-left, stem kept inside the ring, vein cut out so it shows in themed icons. Generated by `tools/icon/`. No saved-file format change; update from the 0.1.11 release APK tested on the emulator |
| 0.1.13 (versionCode 14) | Jersey Mike's in restaurant search: 23 Regular-size subs on white bread (#1–#14 cold, "Mike's Way", and nine hot cheesesteaks), from the chain's Master Menu Label Report PDF. Now ~2,280 items from 10 chains. No saved-file format change; update from the 0.1.12 release APK tested on the emulator (logged food kept, Jersey Mike's search and form checked) |

The release build is signed with **this PC's Android debug key** (`~/.android/debug.keystore`), set in `app/build.gradle.kts`. As a result:
- Updates built **on this PC** install over the old version and keep the user's data.
- An APK built on a different computer has a different key, so Android will refuse to update. The old app would have to be uninstalled, which **deletes its data**. If development moves machines, copy `~/.android/debug.keystore` over or set up a real release keystore.
- This setup isn't acceptable for the Play Store. A real keystore and a non-`com.example` application ID would be needed for that.

To install on another phone: get the APK onto it (Quick Share, USB, Google Drive, email…), open it, and allow "Install unknown apps" for whichever app opened it when Android asks. Each phone keeps its own data; nothing syncs between devices.

## Known gaps / ideas

- History charts have no tap-for-details on a bar yet; tap the day in the calendar instead.
- Notes can only be seen one day at a time (use the arrows on Today). There's no list or search of past notes.
- No on-device UI tests. The `androidTest` dependencies are still declared in `app/build.gradle.kts`, so adding Compose UI tests under `app/src/androidTest/` needs no setup. UI changes were checked by hand on the `Pixel_6` emulator.
- Restaurant items don't carry fruit/veg servings, and customizations (toppings, swaps) have to be added as separate items or edited in the form. Only 10 chains so far; see "Restaurant menus" for which big ones are missing and why.
- Common foods are plain foods only (no mixed dishes like rice and beans). FNDDS has those too; add their codes to `whole_foods.tsv` if wanted.
- Packaged-food search depends on `search.openfoodfacts.org`. See "External services" for the fallback.
- No spreadsheet export. **Save a backup** (Goals tab) writes everything as the app's own JSON, which restores fine but isn't easy to open in Excel or Sheets. There's no CSV export of the food log.
- Fruit/veg goals use the 2,000-calorie pattern for everyone unless the user types their own goal. The guidelines scale them by calorie level, which the app avoids asking about by design.
- The package/applicationId is still `com.example.greetingcard`. Renaming it later counts as a different app, so existing installs wouldn't update. Do it before sharing widely.
- Logged foods only keep text. `FoodViewModel.pickMenuItem` (and `pickWholeFood`, and barcode lookups) turn the item into a `FoodDraft`, and the `FoodEntry` saved from the form stores only the display name, serving label, servings and nutrient numbers. No chain, `MenuItem`, FNDDS code or barcode is kept on the entry. Because of that, past entries can't be traced back to their source: History can't group or filter by restaurant, a corrected number in `restaurants.json` or `foods.json` doesn't reach entries already logged, and the source note isn't shown when an entry is reopened. Saved foods are matched by name and serving label text (`submitFood`), so a renamed menu item creates a new saved food. Fixing this would need an optional source field on `FoodEntry`/`SavedFood`; per the format rules in `AppDataJson`, it must default to absent so old files still load.
