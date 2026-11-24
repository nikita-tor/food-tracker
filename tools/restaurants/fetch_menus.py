"""Downloads restaurant chains' published nutrition info into menus/<id>.tsv, one file per chain.

Each chain's source is set in chains.tsv. All of them are what the chain's own website shows:
  - nutritionix:<slug>  Chains whose nutrition page is a Nutritionix "Interactive Nutrition Menu"
                        (www.nutritionix.com/<slug>/menu/premium, linked from the chain's site).
  - chick-fil-a         The table on chick-fil-a.com/nutrition-allergens.
  - olive-garden        Olive Garden's nutrition PDF. Needs `pdftotext` (Poppler; it comes with Git for Windows).
  - jersey-mikes        Jersey Mike's Master Menu Label Report PDF (linked from jerseymikes.com/menu/nutrition,
                        which itself blocks scripts). Regular subs on white bread only. Also needs `pdftotext`.
  - manual              Typed in by hand from the chain's PDF (Five Guys). Never touched by this script.

The .tsv files are checked in, so a rerun shows up as a reviewable diff. Run build_restaurant_list.py afterwards.
Nutrition values are FDA-rounded as published. "<1 g" and "less than 1 g" are stored as 0.

Usage:
  python fetch_menus.py [<id> ...]     (no ids: every chain that isn't manual)
"""

import csv
import html
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
MENUS = os.path.join(HERE, "menus")
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

COLUMNS = ["category", "name", "serving", "calories", "protein", "fiber", "sodium", "addedSugars"]

# Menu sections left out everywhere: alcohol, and catering trays that serve a group.
SKIP_CATEGORY = re.compile(r"alcohol|beer|wine|spirits|cocktail|margarita|party platter|catering|tray", re.I)

# Per-chain name fixes, applied in order: (regex, replacement, serving or None to keep it).
RENAMES = {
    # A Triple Dipper is three picks, each listed on its own ("TD Big Mouth Bites").
    "chilis": [(r"^TD ", "Triple Dipper pick: ", "1 of your 3 picks")],
}


def read_chains():
    with open(os.path.join(HERE, "chains.tsv"), encoding="utf-8") as f:
        rows = [r for r in csv.reader(f, delimiter="\t") if r and not r[0].startswith("#")]
    header, rows = rows[0], rows[1:]
    return [dict(zip(header, r)) for r in rows]


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept-Language": "en-US"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def number(text):
    """'1,170mg' -> 1170, '<1' -> 0, '' -> None."""
    text = html.unescape(str(text)).strip().lower()
    if not text:
        return None
    if text.startswith("<") or text.startswith("less"):
        return 0
    match = re.search(r"\d[\d,]*(\.\d+)?", text)
    return float(match.group(0).replace(",", "")) if match else None


def clean(name):
    name = html.unescape(name)
    name = re.sub(r"[®™©]", "", name)
    return re.sub(r"\s+", " ", name).strip()


def nutritionix(slug):
    """Rows from the Nutritionix menu table. Columns are found by their header text, since chains differ."""
    page = get(f"https://www.nutritionix.com/{slug}/menu/premium").decode("utf-8")
    headers = {}
    for index, cell in re.findall(r'<th[^>]*id="inmGrid_c(\d+)"[^>]*>(.*?)</th>', page, re.S):
        label = re.sub(r"<[^>]+>", " ", cell).split("Sort by")[0]
        headers[re.sub(r"\s+", " ", label).strip()] = int(index)
    wanted = {
        "calories": "Calories",
        "protein": "Protein (g)",
        "fiber": "Dietary Fiber (g)",
        "sodium": "Sodium (mg)",
        "addedSugars": "Added Sugars (g)",
    }
    missing = [label for key, label in wanted.items() if label not in headers and key != "addedSugars"]
    if missing:
        sys.exit(f"{slug}: table has no {missing} column. Headers: {list(headers)}")

    rows, category = [], ""
    for row_class, row in re.findall(r'<tr class="(subCategory|odd|even)">(.*?)</tr>', page, re.S):
        cells = re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)
        if row_class == "subCategory":
            category = clean(re.sub(r"<[^>]+>", "\n", cells[0]).strip().split("\n")[0])
            continue
        name = re.search(r'class="nmItem" title="([^"]+)"', row)
        if not name:
            continue
        values = [re.sub(r"<[^>]+>", "", c) for c in cells]
        item = {"category": category, "name": clean(name.group(1)), "serving": "1 order"}
        for key, label in wanted.items():
            item[key] = number(values[headers[label]]) if label in headers else None
        rows.append(item)
    return rows


def chick_fil_a():
    """Rows from the data behind chick-fil-a.com's nutrition table. Items with sizes or options list each one."""
    page = get("https://www.chick-fil-a.com/nutrition-allergens").decode("utf-8")
    match = re.search(r'<script id="wp-script-module-data-@wordpress/interactivity" type="application/json">(.*?)</script>',
                      page, re.S)
    state = json.loads(match.group(1))["state"]["nutrition-allergens-table-store"]["tableData"]["nutrition"]
    rows = []
    for section in state:
        for item in section["items"]:
            for entry in item["sub_items"] or [item]:
                fields = {f["label"]: f["value"] for f in entry["fields"]}
                grams = number(fields.get("Serving Size", ""))
                rows.append({
                    "category": clean(section["menu"]),
                    "name": clean(entry["title"]),
                    "serving": f"1 order ({grams:g} g)" if grams else "1 order",
                    "calories": number(fields["Calories"]),
                    "protein": number(fields["Protein (g)"]),
                    "fiber": number(fields["Fiber (g)"]),
                    "sodium": number(fields["Sodium (mg)"]),
                    "addedSugars": None,  # Chick-fil-A publishes total sugars only.
                })
    return rows


def olive_garden():
    """Rows from Olive Garden's PDF. Each item is a name line followed by a line of 11 numbers; "add ..." lines,
    which are toppings for the item above, are left out. Section titles are in capitals."""
    pdf = get("https://media.olivegarden.com/en_us/pdf/olive_garden_nutrition.pdf")
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "og.pdf")
        with open(path, "wb") as f:
            f.write(pdf)
        text = subprocess.run(["pdftotext", "-layout", path, "-"], capture_output=True, check=True).stdout
    lines = text.decode("utf-8", errors="replace").splitlines()

    rows, category, name = [], "", []
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("than ") or re.fullmatch(r"US_\d+", stripped):
            continue
        tokens = re.sub(r"\bless\b", "0", stripped).split()
        numeric = all(re.fullmatch(r"\d[\d,]*(\.\d+)?", t) for t in tokens)
        if numeric and len(tokens) == 11:
            if name and not name[0].lower().startswith("add "):
                # Calories, cal. from fat, fat, sat fat, trans fat, cholesterol, sodium, carbs, fiber, sugars, protein.
                values = [number(t) for t in tokens]
                rows.append({
                    "category": category,
                    "name": clean(" ".join(name)),
                    "serving": "1 order",
                    "calories": values[0],
                    "protein": values[10],
                    "fiber": values[8],
                    "sodium": values[6],
                    "addedSugars": None,
                })
            name = []
        elif re.search(r"Calorie|\(g\)|\(mg\)|^Fat\b|^ol\b|Fiber$", stripped):
            continue  # Table header, repeated on every page.
        elif stripped.isupper() and len(stripped) > 3:
            category, name = stripped.title(), []
        elif numeric:
            name = []  # A row of numbers that didn't parse: drop it rather than guess.
        else:
            name.append(stripped)
    return rows


# Jersey Mike's PDF names cold subs by number only ("#7 - Regular Mike's Way Cold - White Sub") and hot ones by an
# internal name ("Steak Philly"), so the menu names come from the chain's own nutrition calculator.
JERSEY_MIKES_NAMES = {
    1: "#1 BLT", 2: "#2 Jersey Shore's Favorite", 3: "#3 Ham and Provolone", 4: "#4 The Number Four",
    5: "#5 The Super Sub", 6: "#6 Roast Beef and Provolone", 7: "#7 Turkey and Provolone", 8: "#8 Club Sub",
    9: "#9 Club Supreme", 10: "#10 Tuna Fish", 11: "#11 Stickball Special", 12: "#12 Cancro Special",
    13: "#13 The Original Italian", 14: "#14 The Veggie",
    16: "#16 Mike's Chicken Philly", 17: "#17 Mike's Famous Philly", 26: "#26 Bacon Ranch Chicken Cheese Steak",
    31: "#31 California Chicken Cheese Steak", 42: "#42 Chipotle Chicken Cheese Steak", 43: "#43 Chipotle Cheese Steak",
    44: "#44 Buffalo Chicken Cheese Steak", 55: "#55 Big Kahuna Chicken Cheese Steak", 56: "#56 Big Kahuna Cheese Steak",
}


def jersey_mikes():
    """Regular-size subs on white bread from Jersey Mike's Master Menu Label Report, the PDF its nutrition page links
    to. Cold subs are "Mike's Way" (onions, lettuce, tomatoes, oil, vinegar, spices) as listed, except the #1 BLT.
    Each item is a title line, a header and the numbers. Minis, Giants, other breads, wraps and tubs are left out."""
    pdf = get("https://assets.ctfassets.net/lkal9b5unnor/4yCqomJo5gMpiUqaIrrqaD/65d392fba3b86d012e94644712de9774/"
              "Jersey_Mikes_Master_Menu_Label_Report_V2_final.pdf")
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "jm.pdf")
        with open(path, "wb") as f:
            f.write(pdf)
        text = subprocess.run(["pdftotext", "-layout", path, "-"], capture_output=True, check=True).stdout
    lines = [line.strip() for line in text.decode("utf-8", errors="replace").splitlines()]

    title = re.compile(r"#(\d+) - (Regular Mike's (?:Way )?Cold|Hot Regular - .+?) - White Sub$")
    rows = []
    for index, line in enumerate(lines):
        match = title.match(line)
        if not match:
            continue
        number_id, kind = int(match.group(1)), match.group(2)
        if number_id not in JERSEY_MIKES_NAMES:
            sys.exit(f"jersey-mikes: no menu name for #{number_id} ({kind}); add it to JERSEY_MIKES_NAMES")
        # The numbers are lines made only of numbers before "Contains" (a page break can come in between). Usually the
        # first holds the columns up to protein and the second starts with calories, but which line total fat lands on
        # varies, and a table split by a page break is one line. Last line first puts them in column order.
        numbers = []
        for following in lines[index + 1:index + 25]:
            if following.startswith("Contains") or following.startswith("#"):
                break
            tokens = following.split()
            if tokens and all(re.fullmatch(r"\d[\d,]*(\.\d+)?", t) for t in tokens):
                numbers.append([number(t) for t in tokens])
        if not numbers:
            continue  # Listed without nutrition (e.g. a limited-time sub).
        values = [v for line in numbers[-1:] + numbers[:-1] for v in line]
        # Calories, fat cal., total fat, sat. fat, trans fat, cholesterol, sodium, carbs, fiber, sugar, protein.
        # Fat calories are about 9 per gram of fat: a check that the columns were put back in the right order.
        if len(values) != 11 or abs(values[1] - 9 * values[2]) > 20:
            sys.exit(f"jersey-mikes: can't read the numbers for {line}: {numbers}")
        hot = kind.startswith("Hot")
        rows.append({
            "category": "Hot Subs" if hot else "Cold Subs",
            "name": JERSEY_MIKES_NAMES[number_id],
            "serving": "1 Regular sub, white bread" + ("" if hot or number_id == 1 else ", Mike's Way"),
            "calories": values[0],
            "protein": values[10],
            "fiber": values[8],
            "sodium": values[6],
            "addedSugars": None,  # Jersey Mike's publishes total sugars only.
        })
    rows.sort(key=lambda r: (r["category"] != "Cold Subs", int(r["name"][1:].split()[0])))
    return rows


def dedupe(rows):
    """The same item can be listed under several sections (e.g. "New" and its usual one): keep the first.
    If the numbers differ (Olive Garden's lunch and dinner portions), keep each, named after its section."""
    numbers = lambda r: tuple(r[c] for c in COLUMNS[3:])
    by_name = {}
    for row in rows:
        by_name.setdefault(row["name"].lower(), []).append(row)
    result = []
    for row in rows:
        same_name = by_name[row["name"].lower()]
        first_alike = next(r for r in same_name if numbers(r) == numbers(row))
        if first_alike is not row:
            continue
        if len({numbers(r) for r in same_name}) > 1:
            row = dict(row, name=f"{row['name']} ({row['category']})")
        result.append(row)
    return result


def fetch(chain):
    source = chain["source"]
    if source.startswith("nutritionix:"):
        rows = nutritionix(source.split(":", 1)[1])
    elif source == "chick-fil-a":
        rows = chick_fil_a()
    elif source == "olive-garden":
        rows = olive_garden()
    elif source == "jersey-mikes":
        rows = jersey_mikes()
    else:
        sys.exit(f"{chain['id']}: unknown source {source}")
    for pattern, replacement, serving in RENAMES.get(chain["id"], []):
        for row in rows:
            if re.search(pattern, row["name"]):
                row["name"] = re.sub(pattern, replacement, row["name"])
                row["serving"] = serving or row["serving"]
    rows = [r for r in rows if not SKIP_CATEGORY.search(r["category"]) and r["calories"] is not None]
    rows = dedupe(rows)
    # A chain that leaves added sugars blank everywhere doesn't publish them: unknown, not zero.
    if not any(r["addedSugars"] for r in rows):
        for row in rows:
            row["addedSugars"] = None
    return rows


def write(chain, rows):
    path = os.path.join(MENUS, chain["id"] + ".tsv")
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(f"# {chain['name']}, from {chain['page']} (fetched {time.strftime('%Y-%m-%d')} by fetch_menus.py)\n")
        writer = csv.writer(f, delimiter="\t", lineterminator="\n")
        writer.writerow(COLUMNS)
        for row in rows:
            writer.writerow(["" if row[c] is None else f"{row[c]:g}" if isinstance(row[c], float) else row[c]
                             for c in COLUMNS])
    print(f"{chain['id']}: {len(rows)} items -> {os.path.relpath(path, HERE)}")


def main():
    chains = [c for c in read_chains() if c["source"] != "manual"]
    if len(sys.argv) > 1:
        chains = [c for c in chains if c["id"] in sys.argv[1:]]
    for chain in chains:
        write(chain, fetch(chain))
        time.sleep(1)


if __name__ == "__main__":
    main()
