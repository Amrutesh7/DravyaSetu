from pathlib import Path
import re


# Project directories
BASE_DIR = Path(__file__).resolve().parent.parent
EXTRACTED_DIR = BASE_DIR / "knowledge-base" / "extracted"
CLEANED_DIR = BASE_DIR / "knowledge-base" / "cleaned"


def clean_text(text: str) -> str:
    # Normalize line endings
    text = text.replace("\r\n", "\n").replace("\r", "\n")

    # Remove extraction page markers added by our PDF extractor
    text = re.sub(
        r"^\s*===== PAGE \d+ =====\s*$",
        "",
        text,
        flags=re.MULTILINE
    )

    # Remove trailing spaces
    text = re.sub(r"[ \t]+$", "", text, flags=re.MULTILINE)

    # Normalize excessive spaces/tabs inside lines
    text = re.sub(r"[ \t]+", " ", text)

    # Reduce excessive blank lines
    text = re.sub(r"\n{3,}", "\n\n", text)

    # Remove blank space at beginning/end
    text = text.strip()

    return text


def clean_file(input_path: Path) -> None:
    output_path = CLEANED_DIR / input_path.name

    print(f"Cleaning: {input_path.name}")

    text = input_path.read_text(encoding="utf-8", errors="replace")
    cleaned = clean_text(text)

    output_path.write_text(cleaned, encoding="utf-8")

    print(f"Saved: {output_path.name}")


def main() -> None:
    CLEANED_DIR.mkdir(parents=True, exist_ok=True)

    txt_files = sorted(EXTRACTED_DIR.glob("*.txt"))

    if not txt_files:
        print("No TXT files found in knowledge-base/extracted/")
        return

    print(f"Found {len(txt_files)} TXT file(s).\n")

    for txt_path in txt_files:
        try:
            clean_file(txt_path)
        except Exception as error:
            print(f"ERROR processing {txt_path.name}: {error}")

    print("\nCleaning complete.")


if __name__ == "__main__":
    main()