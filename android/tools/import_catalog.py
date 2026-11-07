"""Rebuild the app's voice-free catalog from the user's reviewed extraction.

Run from any directory: python android/tools/import_catalog.py
No live Drive guessing: candidates in drive_matches.json were already reviewed.
"""
import hashlib
import json
from collections import Counter
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT.parent / "extraction_work" / "drive_matches.json"
DESTINATION = PROJECT / "app/src/main/assets/registrations.json"
MAIN = [
    ("ENTRANCE", "Entrance"), ("LHM", "LHM"), ("GLORY", "Glory"),
    ("RESPONSE", "Response"), ("ALLELUIA", "Alleluia"), ("OFFERTORY", "Offertory"),
    ("HOLY", "Holy"), ("PROCLAMATION", "Proclamation"), ("OUR FATHER", "Our father"),
    ("LOG", "LOG"), ("COMMUNION", "Communion"), ("THANKS GIVING", "Thanksgiving"),
    ("RECESSIONAL", "Recessional"),
]
EXTRA = [
    ("Adoration", "Adoration"), ("Ash", "Ash"), ("Mission Wedding", "Mission Wedding"),
    ("SOB", "SOB"), ("Way of the cross", "Way of the cross"),
    ("WE BELIEVE", "We believe"), ("Z-Grade", "Z-Grade"),
]


def section_id(folder):
    return folder.lower().replace(" ", "-")


def main():
    raw = json.loads(SOURCE.read_text(encoding="utf-8"))
    sections = [{"id": section_id(folder), "title": title, "other": index >= len(MAIN)}
                for index, (folder, title) in enumerate(MAIN + EXTRA)]
    rows = []
    for source in raw["rows"]:
        links = [{"title": item["name"], "url": (
            f'https://docs.google.com/document/d/{item["id"]}/edit'
            if "Google Docs" in item["label"] else
            f'https://drive.google.com/file/d/{item["id"]}/view'
        )} for item in source["candidates"]]
        rows.append({
            "id": hashlib.sha256(source["source_path"].encode()).hexdigest()[:20],
            "sectionId": section_id(source["folder"]), "name": source["registration"].strip(),
            "style": source["style"], "tempo": source["tempo"],
            "transpose": source["transpose"], "links": links,
        })
    order = {section["id"]: i for i, section in enumerate(sections)}
    rows.sort(key=lambda row: (order[row["sectionId"]], row["name"].casefold()))
    assert len(rows) == 458 and len({row["id"] for row in rows}) == 458
    assert sum(bool(row["links"]) for row in rows) == 292
    assert sum(len(row["links"]) for row in rows) == 298
    assert sum(row["sectionId"] in [section_id(s[0]) for s in MAIN] for row in rows) == 419
    for section, name in [("entrance", "As we gather may"), ("log", "Peace I give to you"), ("our-father", "Stanes")]:
        assert next(row for row in rows if row["sectionId"] == section and row["name"] == name)["links"] == []
    irish = next(row for row in rows if row["sectionId"] == "recessional" and row["name"] == "Irish")
    assert irish["links"][0]["url"] == "https://drive.google.com/file/d/1bbjzfNMGLuGf-5IoGMdmv5FvFga3U0hN/view"
    data = {"version": 1, "sections": sections, "registrations": rows}
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    DESTINATION.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"registrations": len(rows), "linked": 292, "withoutLinks": 166,
                      "files": 298, "sections": Counter(row["sectionId"] for row in rows)}, indent=2))


if __name__ == "__main__":
    main()
