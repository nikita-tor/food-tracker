"""Builds app/src/main/assets/foods.json, the common-foods list the app searches offline.

Joins three things:
  - whole_foods.tsv (this folder): which foods to include and what to call them.
  - USDA FNDDS, from FoodData Central's "Survey (FNDDS)" JSON download: nutrients per 100 g and portion weights.
  - USDA FPED (Food Patterns Equivalents Database) .xls: cup-equivalents of fruit and vegetables per 100 g.

Both USDA datasets are public domain. The versions should match: FNDDS 2019-2020 is
FoodData_Central_survey_food_json_2022-10-28.zip, and its FPED is FPED_1720.xls. See DEVELOPER.md for links.

Usage (needs `pip install xlrd` for the .xls file):
  python build_food_list.py <fndds.json> <FPED.xls> [<out.json>]
"""

import csv
import json
import os
import re
import sys

import xlrd

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "foods.json")

# FNDDS nutrient numbers -> keys in foods.json. Units: grams, except calories (kcal) and minerals (mg).
NUTRIENTS = {
    "203": "protein",
    "291": "fiber",
    "208": "calories",
    # Not tracked by the app yet; kept so they can be added later without rebuilding the list.
    "205": "carbs",
    "204": "fat",
    "606": "saturatedFat",
    "269": "sugars",
    "307": "sodium",
    "306": "potassium",
    "301": "calcium",
    "303": "iron",
}
GRAMS_PER_TSP_SUGAR = 4.2  # FPED reports added sugars in teaspoon equivalents.
MAX_PORTIONS = 8


def read_list(path):
    foods = []
    with open(path, encoding="utf-8") as f:
        for row in csv.reader(f, delimiter="\t"):
            if not row or row[0].startswith("#"):
                continue
            code, name = row[0].strip(), row[1].strip()
            aliases = [a.strip() for a in row[2].split("|")] if len(row) > 2 and row[2].strip() else []
            foods.append((code, name, aliases))
    return foods


def read_fped(path):
    sheet = xlrd.open_workbook(path).sheet_by_index(0)
    header = [h.split(" ")[0] for h in sheet.row_values(0)]
    rows = {}
    for i in range(1, sheet.nrows):
        values = dict(zip(header, sheet.row_values(i)))
        rows[str(int(values["FOODCODE"]))] = values
    return rows


def portion_label(description):
    """Cleans up FNDDS wording, or returns None for portions that only make sense to survey coders."""
    label = " ".join(description.split())
    lower = label.lower()
    if lower == "quantity not specified" or lower.startswith("guideline amount") or "yields" in lower:
        return None
    # "1 oz, raw" of bacon given as its cooked weight, and drinks measured with ice: confusing.
    if "raw (yield after cooking)" in lower or "(with ice)" in lower:
        return None
    return re.sub(r"\s*\((no ice|NFS|yield after cooking[^)]*)\)", "", label)


def portions(food):
    seen, result = set(), []
    for p in sorted(food["foodPortions"], key=lambda p: p["sequenceNumber"]):
        label = portion_label(p["portionDescription"])
        if label is None or label in seen or p["gramWeight"] <= 0:
            continue
        seen.add(label)
        result.append({"label": label, "grams": round(p["gramWeight"], 1)})
    # Drinks only come in fluid ounces and odd container sizes; a glass is what people log.
    fl_oz = next((p for p in result if p["label"] == "1 fl oz"), None)
    if fl_oz and not any(p["label"].startswith("1 cup") for p in result):
        result.insert(0, {"label": "1 cup (8 fl oz)", "grams": round(fl_oz["grams"] * 8, 1)})
    return result[:MAX_PORTIONS]


def main(fndds_path, fped_path, out_path):
    fndds = {f["foodCode"]: f for f in json.load(open(fndds_path, encoding="utf-8"))["SurveyFoods"]}
    fped = read_fped(fped_path)
    wanted = read_list(os.path.join(HERE, "whole_foods.tsv"))

    names = [name.lower() for _, name, _ in wanted]
    duplicates = {n for n in names if names.count(n) > 1}
    if duplicates:
        sys.exit(f"Duplicate names in whole_foods.tsv: {sorted(duplicates)}")

    out, problems = [], []
    for code, name, aliases in wanted:
        food, eq = fndds.get(code), fped.get(code)
        if food is None or eq is None:
            problems.append(f"{code} {name}: missing from {'FNDDS' if food is None else 'FPED'}")
            continue
        amounts = {n["nutrient"]["number"]: n["amount"] for n in food["foodNutrients"]}
        per100g = {key: round(amounts.get(number, 0.0), 2) for number, key in NUTRIENTS.items()}
        per100g["fruit"] = round(eq["F_TOTAL"], 3)  # Includes 100% juice, which the app counts as fruit.
        # Beans, peas and lentils are vegetables in the Dietary Guidelines; FPED keeps them separate.
        per100g["veg"] = round(eq["V_TOTAL"] + eq["V_LEGUMES"], 3)
        per100g["addedSugars"] = round(eq["ADD_SUGARS"] * GRAMS_PER_TSP_SUGAR, 2)
        entry = {"name": name, "fnddsCode": code, "description": food["description"], "per100g": per100g}
        if aliases:
            entry["aliases"] = aliases
        entry["portions"] = portions(food)
        if not entry["portions"]:
            problems.append(f"{code} {name}: no portions")
        out.append(entry)

    if problems:
        sys.exit("Problems:\n  " + "\n  ".join(problems))

    doc = {
        "source": "USDA FNDDS 2019-2020 (FoodData Central, published 2022-10-28) and USDA FPED 2017-2020. "
        "Public domain.",
        "units": "per100g values are grams, except calories (kcal), sodium/potassium/calcium/iron (mg), "
        "and fruit/veg (cup-equivalent servings).",
        "foods": sorted(out, key=lambda f: f["name"].lower()),
    }
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"Wrote {len(out)} foods to {os.path.normpath(out_path)}")


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) == 4 else DEFAULT_OUT)
