import os
import sys
import pandas as pd

# Make D:\DravyaSetu\ai-service available for imports
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from app.preprocessing.quality_checker import ImageQualityChecker

BASE_DIR = r"D:\DravyaSetu\datasets\v2_expansion"

INPUT = os.path.join(BASE_DIR, "metadata", "inat_deduplicated_manifest.csv")
OUTPUT = os.path.join(BASE_DIR, "metadata", "inat_quality_manifest.csv")
FAILURES = os.path.join(BASE_DIR, "metadata", "inat_quality_rejections.csv")

df = pd.read_csv(INPUT)

checker = ImageQualityChecker(
    min_width=384,
    min_height=384,
    min_blur_score=35.0,
    min_brightness=25.0,
    max_brightness=235.0,
    min_quality_score=0.45,
    min_aspect_ratio=0.25,
    max_aspect_ratio=4.0
)

results = []
rejections = []

total = len(df)

for i, row in df.iterrows():

    path = row["local_path"]

    try:
        result = checker.check(path)

        result_dict = {
            k: getattr(result, k)
            for k in result.__dataclass_fields__
        }

        accepted = bool(result_dict.get("acceptable", False))

        record = row.to_dict()
        record.update(result_dict)
        record["quality_status"] = (
            "ACCEPTED" if accepted else "REJECTED"
        )

        results.append(record)

        if not accepted:
            rejections.append(record)

    except Exception as e:

        record = row.to_dict()
        record["quality_status"] = "ERROR"
        record["quality_error"] = str(e)

        results.append(record)
        rejections.append(record)

    if (i + 1) % 250 == 0:
        accepted_count = sum(
            x.get("quality_status") == "ACCEPTED"
            for x in results
        )

        print(
            f"[{i+1}/{total}] "
            f"accepted={accepted_count} "
            f"rejected/errors={len(rejections)}"
        )

result_df = pd.DataFrame(results)

result_df.to_csv(
    OUTPUT,
    index=False
)

pd.DataFrame(rejections).to_csv(
    FAILURES,
    index=False
)

print()
print("======================================")
print("iNaturalist quality validation")
print("======================================")
print(f"Input images:       {total}")
print(
    f"Accepted:           "
    f"{(result_df['quality_status'] == 'ACCEPTED').sum()}"
)
print(
    f"Rejected:           "
    f"{(result_df['quality_status'] == 'REJECTED').sum()}"
)
print(
    f"Errors:             "
    f"{(result_df['quality_status'] == 'ERROR').sum()}"
)
print()
print(f"Quality manifest:   {OUTPUT}")
print(f"Rejections:         {FAILURES}")

print()
print("Accepted images by class:")
print(
    result_df[
        result_df["quality_status"] == "ACCEPTED"
    ]["class_name"]
    .value_counts()
    .sort_index()
    .to_string()
)
