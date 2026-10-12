from pathlib import Path
import shutil

SRC = Path(r"D:\DravyaSetu\datasets\v2_expansion\raw\iNaturalist_Lemon_Nagadali_Remaining")
DEST = Path(r"D:\DravyaSetu\datasets\clean_candidate_pool")

added = 0
lemon = 0
nagadali = 0

for class_name in ["Lemon_grass", "Nagadali"]:
    src_dir = SRC / class_name
    dest_dir = DEST / class_name
    dest_dir.mkdir(parents=True, exist_ok=True)

    for img in src_dir.iterdir():
        if img.is_file():
            target = dest_dir / img.name

            if not target.exists():
                shutil.copy2(img, target)
                added += 1

                if class_name == "Lemon_grass":
                    lemon += 1
                else:
                    nagadali += 1

print("DONE")
print("Added:", added)
print("Lemon_grass added:", lemon)
print("Nagadali added:", nagadali)