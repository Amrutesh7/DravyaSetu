
from pathlib import Path
import json
import re

BASE_DIR = Path(__file__).resolve().parent.parent
CLEANED_DIR = BASE_DIR / "knowledge-base" / "cleaned"
OUTPUT_DIR = BASE_DIR / "knowledge-base" / "mapped"
PLANT_CLASSES = (
    BASE_DIR.parent / "shared" / "constants" / "plant-classes.json"
)

FILES = [
    "Betel, Castor, Ekka, Jasmine, Mango, Pepper, Raktachandini and Wood_sorel..txt",
    "Ayurvedic Pharmacopoeia of India All Volume-Remaining Plants.txt",
]

# Matches numbered monograph headings, not ordinary plant-name mentions.
HEADING = re.compile(
    r"^\s*(\d+)\.\s+([^\r\n]+?)\s*$",
    re.MULTILINE,
)


def load_plant_classes():
    data = json.loads(PLANT_CLASSES.read_text(encoding="utf-8"))
    return data["classes"]


def extract_sections(text):
    matches = list(HEADING.finditer(text))
    sections = []

    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)

        sections.append({
            "number": match.group(1),
            "heading": match.group(2).strip(),
            "text": text[start:end].strip(),
        })

    return sections


def main():
    plants = load_plant_classes()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    report = []

    for filename in FILES:
        source_path = CLEANED_DIR / filename

        if not source_path.exists():
            print(f"Missing source: {filename}")
            continue

        text = source_path.read_text(encoding="utf-8", errors="replace")
        sections = extract_sections(text)

        # Keep outputs separated by source to avoid filename collisions.
        source_folder = OUTPUT_DIR / source_path.stem.replace(".", "_")
        source_folder.mkdir(parents=True, exist_ok=True)

        for index, section in enumerate(sections, start=1):
            output_name = f"section_{index:04d}.txt"
            (source_folder / output_name).write_text(
                section["text"], encoding="utf-8"
            )

            report.append({
                "source_file": filename,
                "section_file": str(source_folder / output_name),
                "heading": section["heading"],
                "section_number": section["number"],
                "plant_id": None,
                "mapping_status": "REVIEW_REQUIRED",
            })

        print(f"{filename}: extracted {len(sections)} candidate sections")

    report_path = OUTPUT_DIR / "section_mapping_review.json"
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"\nPlant classes loaded: {len(plants)}")
    print(f"Candidate sections: {len(report)}")
    print(f"Review report: {report_path}")
    print("No original cleaned source files were modified.")
    print("Plant IDs are intentionally left unmapped pending validation.")


if __name__ == "__main__":
    main()
