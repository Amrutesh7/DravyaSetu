from pathlib import Path
import pymupdf


# Project directories
BASE_DIR = Path(__file__).resolve().parent.parent
RAW_DIR = BASE_DIR / "knowledge-base" / "raw"
EXTRACTED_DIR = BASE_DIR / "knowledge-base" / "extracted"


def extract_pdf(pdf_path: Path) -> None:
    output_path = EXTRACTED_DIR / f"{pdf_path.stem}.txt"

    print(f"Extracting: {pdf_path.name}")

    document = pymupdf.open(pdf_path)

    with output_path.open("w", encoding="utf-8") as output_file:
        for page_number, page in enumerate(document, start=1):
            text = page.get_text("text")

            output_file.write(f"\n\n===== PAGE {page_number} =====\n\n")
            output_file.write(text)

    document.close()

    print(f"Saved: {output_path.name}")


def main() -> None:
    EXTRACTED_DIR.mkdir(parents=True, exist_ok=True)

    pdf_files = sorted(RAW_DIR.glob("*.pdf"))

    if not pdf_files:
        print("No PDF files found in knowledge-base/raw/")
        return

    print(f"Found {len(pdf_files)} PDF(s).\n")

    for pdf_path in pdf_files:
        try:
            extract_pdf(pdf_path)
        except Exception as error:
            print(f"ERROR processing {pdf_path.name}: {error}")

    print("\nExtraction complete.")


if __name__ == "__main__":
    main()