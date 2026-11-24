# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Despite the folder/package name (`greetingcard`, left over from the Android Studio template), this is **Food Tracker**: a single-module Jetpack Compose app that tracks protein, fiber, and fruit/veg servings against US-guideline goals, with calories optional. `DEVELOPER.md` is the full developer guide (features table, goal sources, release process, troubleshooting) — read it before larger changes and keep it updated. `ROOMMATE.md` is the end-user install/usage note for the person the APK is shared with.

## Commands

Use `./gradlew` from Bash or `.\gradlew.bat` from PowerShell.

| Command | Purpose |
| --- | --- |
| `./gradlew spotlessApply check` | **Run before calling a change done.** Formats, then runs ktlint + Android Lint + unit tests |
| `./gradlew :app:testDebugUnitTest` | Unit tests only |
| `./gradlew :app:testDebugUnitTest --tests "com.example.greetingcard.GuidelinesTest"` | One test class (append `.methodName` for one test) |
| `./gradlew :app:lint` | Android Lint; report at `app/build/reports/lint-results-debug.html` |
| `./gradlew :app:installDebug` | Build and install on connected device/emulator |
| `./gradlew :app:assembleRelease` | Release APK (signed with this PC's debug key; see "Sharing the app" in `DEVELOPER.md`) |

Lint is strict: Android Lint has `warningsAsErrors = true` and no baseline; ktlint (via Spotless, rules in `.editorconfig`, 120-char lines, trailing commas, LF endings) includes the Compose rule set, and `no-unused-imports` is enabled explicitly (it's off by default with this code style). After editing `.editorconfig`, run `./gradlew --stop` so the daemon picks it up. Suppress only with a comment explaining why.

Unit tests are plain JVM tests (`app/src/test`); `org.json` is added as a test dependency because Android's version is a stub there. There are no instrumented/UI tests — UI changes are verified by hand on the `Pixel_6` emulator, which must be started with `emulator -avd Pixel_6 -gpu swiftshader_indirect` or screenshots come back black.

## Architecture

Deliberately dependency-light: no Room, no Navigation, no DI, no icon library (icons are hand-written vectors in `res/drawable/ic_*.xml`). The only non-template library is Google's `play-services-code-scanner`. Don't add libraries casually.

- **State:** `FoodViewModel` (an `AndroidViewModel`) holds one immutable `AppData` in Compose `mutableStateOf`. Every mutation goes through the private `update { d -> d.copy(...) }`, which replaces the state and persists it. Pass `saveDelayMs` for high-frequency edits (daily notes debounce at 600 ms); `MainActivity.onStop` calls `flushPendingSave()` so pending saves aren't lost.
- **Persistence:** `data/Store.kt` writes everything to one file, `filesDir/food_tracker.json` (temp file + rename, serialized with a Mutex; an unparseable file is renamed to `food_tracker.corrupt-<time>.json`, never overwritten). The format lives in `AppDataJson`. Backups (`data/Backup.kt`) wrap the same JSON, so format changes reach them too. **It may only gain optional fields with defaults** — older files must keep loading (`AppDataJsonTest.filesFromBeforeNamesAndNotesStillLoad` guards this). If you change the format, add a test case for old files.
- **Navigation:** `FoodViewModel.backStack` is a list of `Screen` (in `Screen.kt`); `MainActivity` renders the top entry and `BackHandler` pops it. Bottom tabs reset the stack. The welcome screen is not on the stack — it's shown while `needsName` (blank `Settings.name`) is true.
- **Compose layering:** only screen-level composables (`*Screen`, `TabScaffold`, `TodayTopBar`) receive the `FoodViewModel`; everything below takes plain values and callbacks. The ktlint `vm-forwarding-check` rule enforces this via `compose_allowed_forwarding` in `.editorconfig` — add new screens there or, better, give them plain parameters.
- **Domain rules:**
  - Nutrients are stored **per serving**; `FoodEntry.total` multiplies by `servings`. A saved meal stores the summed totals as one serving.
  - `CalorieMode` (SHOW / HIDE / OFF): HIDE stores but never displays calories; OFF strips them before saving (`applyCalorieMode`). Calories are never a goal — don't add a calorie target.
  - Goals can be turned off (`Settings.hiddenNutrients`): hidden everywhere but still recorded, like calorie HIDE. Use `vm.goals`/`vm.trackedNutrients` for display; `vm.allGoals` only in the Goals tab.
  - Limits (sodium, added sugars) are things to stay under, separate from goals (`LimitNutrient`/`Limit`), **off by default** (`Settings.enabledLimits`). `Nutrients.sodium`/`addedSugars` are nullable: null = unknown, never treat it as 0. Limits don't count toward "goals met".
  - Goal math is pure Kotlin in `data/Guidelines.kt` (unit-tested), with user overrides in `Settings.customGoals`. Each goal displays its government source in the Goals tab; keep sources accurate if rules change.
  - `ui/Format.kt` is Compose-free so it can be unit-tested.
- **Barcode lookup:** `data/OpenFoodFacts.kt` calls the free Open Food Facts v2 API (no key; ~15 lookups/min per device; ODbL attribution is shown in the Goals tab). Previously saved barcodes are quick-added without a network call. Packaged-food text search (`OpenFoodFacts.search`, `search.openfoodfacts.org`) runs only on a button press, never as you type; a picked result goes through the barcode lookup.
- **Network:** the only web requests are the two in `OpenFoodFacts.kt`, each made right after a user tap. Any new or changed network call must be added to "Network usage" in `DEVELOPER.md`.
- **Common foods:** `assets/foods.json` (~320 whole foods, offline search in `data/WholeFoods.kt`) is **generated** from USDA FNDDS + FPED by `tools/foods/build_food_list.py` from `tools/foods/whole_foods.tsv`. Edit the TSV and rerun; never hand-edit the JSON. Steps are in "Common foods list" in `DEVELOPER.md`.
- **Restaurant menus:** `assets/restaurants.json` (~2,280 items, 10 chains, offline search in `data/Restaurants.kt`) is **generated** by `tools/restaurants/build_restaurant_list.py` from `tools/restaurants/menus/*.tsv`, which `fetch_menus.py` downloads from each chain's own published nutrition info (Five Guys is hand-typed). Only use a chain's own source, never a third-party database. Fruit/veg are 0 and unpublished added sugars stay null. Steps are in "Restaurant menus" in `DEVELOPER.md`.
- **Theme:** fixed green palette in `ui/theme/Color.kt` (dynamic color intentionally off). `colorScheme.card` is the card surface; citrus `tertiary` means "goal met". `res/values*/colors.xml`/`themes.xml` match the launch background to avoid a white flash in dark mode. The launcher icon's foreground is generated by `tools/icon/make_fg.py` (rerun it, don't hand-edit the XML); `tools/icon/centroid.py` checks it stays visually centered.

## Releases

`minSdk` 26 (for `java.time` without desugaring). For a shareable build: bump `versionCode`/`versionName` in `app/build.gradle.kts`, build, copy to `dist/FoodTracker.apk`, and add a row to the release history in `DEVELOPER.md` (and update `ROOMMATE.md` if user-visible). The applicationId `com.example.greetingcard` and the debug-key signing must not change casually — either one makes existing installs unable to update without losing data.
