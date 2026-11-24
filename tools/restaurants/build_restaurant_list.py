"""Builds app/src/main/assets/restaurants.json, the restaurant menus the app searches offline.

Reads chains.tsv (which chains, their names and source pages) and menus/<id>.tsv (one menu per chain, written by
fetch_menus.py or typed in by hand). Checks every row, then writes one JSON file. Nutrients are per menu item as
served. An empty added-sugars cell means the chain doesn't publish it (unknown, not zero).

Usage:
  python build_restaurant_list.py [<out.json>]
"""

import csv
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "restaurants.json")
NUMBERS = ["calories", "protein", "fiber", "sodium", "addedSugars"]


def read_tsv(path):
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    comments = [line for line in lines if line.startswith("#")]
    rows = list(csv.reader([line for line in lines if line and not line.startswith("#")], delimiter="\t"))
    return comments, [dict(zip(rows[0], r)) for r in rows[1:]]


def number(chain, row, key):
    text = row.get(key, "").strip()
    if not text:
        if key == "addedSugars":
            return None
        sys.exit(f"{chain}: {row['name']} has no {key}")
    value = float(text)
    if value < 0:
        sys.exit(f"{chain}: {row['name']} has a negative {key}")
    return int(value) if value.is_integer() else value


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    _, chain_rows = read_tsv(os.path.join(HERE, "chains.tsv"))
    chains, items = [], []
    for index, chain in enumerate(chain_rows):
        comments, rows = read_tsv(os.path.join(HERE, "menus", chain["id"] + ".tsv"))
        date = re.search(r"\d{4}-\d{2}-\d{2}", comments[0] if comments else "")
        if not date:
            sys.exit(f"{chain['id']}.tsv: the first line should say when the menu was fetched (YYYY-MM-DD)")
        chains.append({
            "name": chain["name"],
            "aliases": [a.strip() for a in chain["aliases"].split("|") if a.strip()],
            "page": chain["page"],
            "checked": date.group(0),
        })
        names = set()
        for row in rows:
            if row["name"].lower() in names:
                sys.exit(f"{chain['id']}: {row['name']} is listed twice")
            names.add(row["name"].lower())
            item = {"chain": index, "name": row["name"], "serving": row["serving"]}
            item.update({key: number(chain["id"], row, key) for key in NUMBERS})
            # Protein alone can't have more calories than the item: catches columns read in the wrong order.
            if item["protein"] * 4 > item["calories"] + 20:
                sys.exit(f"{chain['id']}: {row['name']} has {item['protein']} g protein but {item['calories']} calories")
            items.append({k: v for k, v in item.items() if v is not None})
        print(f"{chain['name']}: {len(rows)} items, fetched {date.group(0)}")

    data = {
        "source": "Each chain's published nutrition info (see chains[].page). Values are per menu item as served.",
        "units": "calories (kcal), protein/fiber/addedSugars (g), sodium (mg). No addedSugars = not published.",
        "chains": chains,
        "items": items,
    }
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(items)} items -> {os.path.relpath(out)} ({os.path.getsize(out) // 1024} KB)")


if __name__ == "__main__":
    main()
