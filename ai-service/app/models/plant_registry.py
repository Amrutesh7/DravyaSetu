from pathlib import Path
import json


# DravyaSetu repository root
REPO_ROOT = Path(__file__).resolve().parents[3]

PLANT_CLASSES_FILE = (
    REPO_ROOT
    / "shared"
    / "constants"
    / "plant-classes.json"
)


def load_plant_registry():
    """
    Load the authoritative DravyaSetu plant registry.

    Returns:
        dict:
            {
                "Aloe_Vera": {
                    "plant_id": "PLANT_001",
                    "common_name": "Aloe Vera",
                    "scientific_name": "Aloe vera (L.) Burm.f."
                },
                ...
            }
    """

    if not PLANT_CLASSES_FILE.exists():
        raise FileNotFoundError(
            f"Plant registry not found: {PLANT_CLASSES_FILE}"
        )

    with open(
        PLANT_CLASSES_FILE,
        "r",
        encoding="utf-8"
    ) as file:

        data = json.load(file)

    registry = {}

    for plant_id, plant_data in data["classes"].items():

        plant_identifier = plant_data["plant_identifier"]

        registry[plant_identifier] = {
            "plant_id": plant_id,
            "common_name": plant_data["common_name"],
            "scientific_name": plant_data["scientific_name"],
            "ayurvedic_regional_names":
                plant_data.get(
                    "ayurvedic_regional_names",
                    []
                )
        }

    return registry


def get_plant_by_name(plant_name: str):
    """
    Get plant information using the dataset folder/class name.
    """

    registry = load_plant_registry()

    if plant_name not in registry:
        raise KeyError(
            f"Unknown plant class: {plant_name}"
        )

    return registry[plant_name]


def get_plant_by_id(plant_id: str):
    """
    Get plant information using the official plant_id.
    """

    registry = load_plant_registry()

    for plant_name, plant_data in registry.items():

        if plant_data["plant_id"] == plant_id:

            return {
                "plant_identifier": plant_name,
                **plant_data
            }

    raise KeyError(
        f"Unknown plant_id: {plant_id}"
    )