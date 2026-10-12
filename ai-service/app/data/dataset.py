"""
DravyaSetu V2 - Dataset Loader

Expected dataset structure:

dataset_root/
├── train/
│   ├── Aloe_Vera/
│   ├── Amla/
│   └── ...
├── validation/
│   ├── Aloe_Vera/
│   ├── Amla/
│   └── ...
└── test/
    ├── Aloe_Vera/
    ├── Amla/
    └── ...

Important:
- Exactly 34 canonical classes.
- Class folder names must match the canonical class names.
- Validation and test images must remain untouched.
- Augmentation belongs in the training transform, not here.
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple

from PIL import Image
from torch.utils.data import Dataset


# ----------------------------------------------------------------------
# Canonical DravyaSetu V2 classes
# ----------------------------------------------------------------------

CLASS_NAMES = [
    "Aloe_Vera",
    "Amla",
    "Amruta_Balli",
    "Arali",
    "Ashoka",
    "Ashwagandha",
    "Betel",
    "Betel_Nut",
    "Brahmi",
    "Castor",
    "Curry_Leaf",
    "Doddapatre",
    "Ekka",
    "Ganike",
    "Henna",
    "Hibiscus",
    "Honge",
    "Insulin",
    "Jasmine",
    "Lemon",
    "Lemon_grass",
    "Mango",
    "Mint",
    "Nagadali",
    "Neem",
    "Nithyapushpa",
    "Nooni",
    "Pappaya",
    "Pepper",
    "Pomegranate",
    "Raktachandini",
    "Rose",
    "Tulasi",
    "Wood_sorel",
]

CLASS_TO_INDEX: Dict[str, int] = {
    class_name: index
    for index, class_name in enumerate(CLASS_NAMES)
}

SUPPORTED_EXTENSIONS = {
    ".jpg",
    ".jpeg",
    ".png",
    ".bmp",
    ".webp",
}


# ----------------------------------------------------------------------
# Sample representation
# ----------------------------------------------------------------------

@dataclass(frozen=True)
class DatasetSample:
    image_path: Path
    class_name: str
    class_index: int


# ----------------------------------------------------------------------
# Dataset
# ----------------------------------------------------------------------

class DravyaSetuDataset(Dataset):
    """
    PyTorch dataset for one split.

    This class only loads images and labels.
    Training augmentation is intentionally handled separately.
    """

    def __init__(
        self,
        root_dir: str | Path,
        transform=None,
    ):
        self.root_dir = Path(root_dir)
        self.transform = transform

        if not self.root_dir.exists():
            raise FileNotFoundError(
                f"Dataset directory does not exist: {self.root_dir}"
            )

        self.samples = self._collect_samples()

        if not self.samples:
            raise RuntimeError(
                f"No valid images found in {self.root_dir}"
            )

    # ------------------------------------------------------------------
    # Collect samples
    # ------------------------------------------------------------------

    def _collect_samples(self) -> List[DatasetSample]:

        samples: List[DatasetSample] = []

        for class_name in CLASS_NAMES:

            class_dir = self.root_dir / class_name

            if not class_dir.exists():
                raise FileNotFoundError(
                    f"Missing class directory: {class_dir}"
                )

            class_index = CLASS_TO_INDEX[class_name]

            for image_path in sorted(class_dir.iterdir()):

                if not image_path.is_file():
                    continue

                if image_path.suffix.lower() not in SUPPORTED_EXTENSIONS:
                    continue

                samples.append(
                    DatasetSample(
                        image_path=image_path,
                        class_name=class_name,
                        class_index=class_index,
                    )
                )

        return samples

    # ------------------------------------------------------------------
    # Dataset interface
    # ------------------------------------------------------------------

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(
        self,
        index: int,
    ) -> Tuple[object, int]:

        sample = self.samples[index]

        try:
            image = Image.open(sample.image_path).convert("RGB")
        except Exception as exc:
            raise RuntimeError(
                f"Failed to load image: {sample.image_path}"
            ) from exc

        if self.transform is not None:
            image = self.transform(image)

        return image, sample.class_index

    # ------------------------------------------------------------------
    # Metadata helpers
    # ------------------------------------------------------------------

    def get_sample(self, index: int) -> DatasetSample:
        return self.samples[index]

    def class_counts(self) -> Dict[str, int]:

        counts = {
            class_name: 0
            for class_name in CLASS_NAMES
        }

        for sample in self.samples:
            counts[sample.class_name] += 1

        return counts


# ----------------------------------------------------------------------
# Factory
# ----------------------------------------------------------------------

def build_dataset(
    root_dir: str | Path,
    transform=None,
) -> DravyaSetuDataset:

    return DravyaSetuDataset(
        root_dir=root_dir,
        transform=transform,
    )


# ----------------------------------------------------------------------
# Dataset validation helper
# ----------------------------------------------------------------------

def validate_dataset_structure(
    root_dir: str | Path,
) -> Dict[str, object]:
    """
    Validate the folder structure before creating a Dataset.

    This does NOT replace the final V2 dataset verification gate.
    """

    root = Path(root_dir)

    if not root.exists():
        raise FileNotFoundError(
            f"Dataset root does not exist: {root}"
        )

    actual_dirs = {
        item.name
        for item in root.iterdir()
        if item.is_dir()
    }

    expected_dirs = set(CLASS_NAMES)

    missing = sorted(
        expected_dirs - actual_dirs
    )

    extra = sorted(
        actual_dirs - expected_dirs
    )

    return {
        "valid": len(missing) == 0 and len(extra) == 0,
        "missing_classes": missing,
        "extra_classes": extra,
        "class_count": len(actual_dirs),
    }


# ----------------------------------------------------------------------
# Smoke test
# ----------------------------------------------------------------------

if __name__ == "__main__":
    print("Running dataset loader smoke test...")

    import tempfile
    import numpy as np

    with tempfile.TemporaryDirectory() as temp_dir:
        root = Path(temp_dir)

        # Create all 34 canonical class directories
        for class_name in CLASS_NAMES:
            (root / class_name).mkdir(parents=True)

        # Create one test image
        test_class = "Aloe_Vera"

        image = Image.fromarray(
            np.zeros((128, 128, 3), dtype=np.uint8)
        )

        image.save(
            root / test_class / "test_image.jpg"
        )

        # Validate folder structure
        structure = validate_dataset_structure(root)

        print("Folder structure valid:", structure["valid"])
        print("Class count:", structure["class_count"])

        # Build dataset
        dataset = DravyaSetuDataset(
            root_dir=root,
            transform=None
        )

        print("Dataset length:", len(dataset))

        # Read sample
        sample_image, sample_label = dataset[0]

        print("Image size:", sample_image.size)
        print("Class index:", sample_label)
        print(
            "Class name:",
            dataset.get_sample(0).class_name
        )

        print(
            "Non-empty classes:",
            {
                k: v
                for k, v in dataset.class_counts().items()
                if v > 0
            }
        )

        # Assertions
        assert structure["valid"] is True
        assert structure["class_count"] == 34
        assert len(dataset) == 1
        assert sample_label == CLASS_TO_INDEX["Aloe_Vera"]

    print()
    print("Dataset loader smoke test: PASSED")