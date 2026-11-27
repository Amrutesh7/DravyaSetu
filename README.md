# 🌿 DravyaSetu AI Service

> **Ayurvedic Medicinal Plant Identification API** — Upload leaf images, get instant plant identification with Grad-CAM visual explanations.

DravyaSetu AI is a deep learning–powered REST API that identifies **34 Ayurvedic medicinal plants** from leaf photographs. Built with **EfficientNetV2-S**, it supports single and multi-image analysis, returns confidence-ranked alternatives, and provides **Grad-CAM heatmaps** showing exactly which leaf features the model focused on.

---

## 🚀 Live API

| Environment | URL |
|-------------|-----|
| **Production (Modal)** | [https://a760-st--dravyasetu-ai-fastapi-app.us-east.modal.run/docs](https://a760-st--dravyasetu-ai-fastapi-app.us-east.modal.run/docs) |
| **Local Development** | [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs) |

---

## ✨ Features

- 🔍 **34 Medicinal Plant Classification** — trained on 1,492 curated images from iNaturalist
- 📸 **Multi-Image Fusion** — upload up to 8 images for consensus-based identification
- 🎯 **Confidence Scoring** — calibrated softmax probabilities with 0.60 acceptance threshold
- 🔥 **Grad-CAM Heatmaps** — base64 JPEG visual explanations for every uploaded image
- 🛡️ **Image Quality Validation** — automatic rejection of blurry, dark, or low-resolution images
- 📋 **Alternatives & Similar Species** — ranked list of other candidate plants
- ⚡ **No API Key Required** — public, unauthenticated endpoint

---

## 📡 API Reference

### `POST /ai/analyze`

Upload one or more plant images for identification.

**Request** (`multipart/form-data`):

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `images` | `File[]` | ✅ | 1–8 JPEG/PNG images (max 12 MB each, 64 MB total) |
| `request_id` | `string` | ❌ | Optional tracking ID (auto-generated if omitted) |

**Example (cURL):**

```bash
curl -X POST "https://a760-st--dravyasetu-ai-fastapi-app.us-east.modal.run/ai/analyze" \
  -F "images=@leaf1.jpg" \
  -F "images=@leaf2.jpg" \
  -F "request_id=req-001"
```

**Example (Python):**

```python
import requests

files = [
    ('images', open('leaf1.jpg', 'rb')),
    ('images', open('leaf2.jpg', 'rb')),
]
response = requests.post(
    'https://a760-st--dravyasetu-ai-fastapi-app.us-east.modal.run/ai/analyze',
    files=files,
    data={'request_id': 'req-001'}
)
print(response.json())
```

**Example (JavaScript):**

```javascript
const formData = new FormData();
formData.append('images', file1);
formData.append('images', file2);
formData.append('request_id', 'req-001');

const res = await fetch(
  'https://a760-st--dravyasetu-ai-fastapi-app.us-east.modal.run/ai/analyze',
  { method: 'POST', body: formData }
);
const data = await res.json();
```

---

## 📦 Response Schema

### ✅ Known Plant (Identified Successfully)

```json
{
  "request_id": "req-001",
  "model_version": "efficientnetv2-s-dravyasetu-v2",
  "image_quality": {
    "acceptable": true,
    "issues": []
  },
  "identification": {
    "status": "KNOWN",
    "plant_id": "PLANT_003",
    "confidence": 0.9993
  },
  "alternatives": [
    {
      "plant_id": "PLANT_034",
      "common_name": "Indian Sorrel / Creeping Wood Sorrel",
      "scientific_name": "Oxalis corniculata L.",
      "confidence": 0.00009
    }
  ],
  "similar_species": [],
  "explanation": {
    "method": "GRAD_CAM",
    "heatmaps": [
      {
        "image_index": 1,
        "image_data": "<base64-encoded JPEG heatmap>",
        "media_type": "image/jpeg"
      }
    ]
  },
  "multi_image": {
    "used": true,
    "image_count": 2
  },
  "warnings": []
}
```

### ❓ Unknown Plant (Low Confidence / Not in Registry)

```json
{
  "request_id": "req-002",
  "model_version": "efficientnetv2-s-dravyasetu-v2",
  "image_quality": {
    "acceptable": true,
    "issues": []
  },
  "identification": {
    "status": "UNKNOWN",
    "plant_id": null,
    "confidence": 0.5745
  },
  "alternatives": [],
  "similar_species": [],
  "explanation": {
    "method": "GRAD_CAM",
    "heatmaps": [
      {
        "image_index": 1,
        "image_data": "<base64-encoded JPEG heatmap>",
        "media_type": "image/jpeg"
      }
    ]
  },
  "multi_image": {
    "used": false,
    "image_count": 1
  },
  "warnings": []
}
```

### ❌ Bad Image Quality (Rejected)

```json
{
  "request_id": "req-003",
  "model_version": "efficientnetv2-s-dravyasetu-v2",
  "image_quality": {
    "acceptable": false,
    "issues": ["TOO_DARK", "BLURRY"]
  },
  "identification": {
    "status": "UNKNOWN",
    "plant_id": null,
    "confidence": 0.0
  },
  "alternatives": [],
  "similar_species": [],
  "explanation": {
    "method": "GRAD_CAM",
    "heatmaps": []
  },
  "multi_image": {
    "used": false,
    "image_count": 1
  },
  "warnings": [
    "The image is too dark. Capture it in better lighting.",
    "The image appears blurry. Hold the camera steady and retry.",
    "Image 'photo.jpg' did not pass the image-quality checks."
  ]
}
```

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `request_id` | `string` | Echo of the request tracking ID |
| `model_version` | `string` | Model identifier (`efficientnetv2-s-dravyasetu-v2`) |
| `image_quality.acceptable` | `bool` | Whether all images passed quality checks |
| `image_quality.issues` | `string[]` | Issue codes: `LOW_RESOLUTION`, `BLURRY`, `TOO_DARK`, `OVEREXPOSED`, `INVALID_ASPECT_RATIO` |
| `identification.status` | `"KNOWN" \| "UNKNOWN"` | Whether the plant was confidently identified |
| `identification.plant_id` | `string \| null` | Registry ID (e.g. `PLANT_003`) or `null` if unknown |
| `identification.confidence` | `float` | Model confidence (0.0–1.0) |
| `alternatives` | `object[]` | Other candidate plants ranked by confidence |
| `similar_species` | `object[]` | Visually similar species |
| `explanation.method` | `"GRAD_CAM"` | Explainability method used |
| `explanation.heatmaps` | `object[]` | Base64-encoded JPEG heatmaps per image |
| `multi_image.used` | `bool` | Whether multi-image fusion was applied |
| `multi_image.image_count` | `int` | Number of images processed |
| `warnings` | `string[]` | Human-readable quality warnings |

---

## 🌱 Supported Plants (34 Classes)

| Class Name | Plant ID | Common Name | Scientific Name |
|------------|----------|-------------|-----------------|
| Aloevera | PLANT_001 | Aloe Vera | *Aloe vera* (L.) Burm.f. |
| Amla | PLANT_002 | Indian Gooseberry | *Phyllanthus emblica* L. |
| Amruta_Balli | PLANT_003 | Heart-leaved Moonseed | *Tinospora cordifolia* (Willd.) Miers |
| Arali | PLANT_004 | Sacred Fig / Peepal Tree | *Ficus religiosa* L. |
| Ashoka | PLANT_005 | Ashoka Tree | *Saraca asoca* (Roxb.) Willd. |
| Ashwagandha | PLANT_006 | Indian Ginseng / Winter Cherry | *Withania somnifera* (L.) Dunal |
| Betel | PLANT_007 | Betel Leaf | *Piper betle* L. |
| Betel_Nut | PLANT_008 | Areca Nut / Betel Nut Palm | *Areca catechu* L. |
| Brahmi | PLANT_009 | Water Hyssop | *Bacopa monnieri* (L.) Wettst. |
| Castor | PLANT_010 | Castor Bean Plant | *Ricinus communis* L. |
| Curry_Leaf | PLANT_011 | Curry Leaf | *Murraya koenigii* (L.) Spreng. |
| Doddapatre | PLANT_012 | Indian Borage / Mexican Mint | *Plectranthus amboinicus* (Lour.) Spreng. |
| Ekka | PLANT_013 | Crown Flower / Giant Milkweed | *Calotropis gigantea* (L.) Dryand. |
| Ganike | PLANT_014 | Black Nightshade / Makoi | *Solanum nigrum* L. |
| Henna | PLANT_015 | Henna | *Lawsonia inermis* L. |
| Hibiscus | PLANT_016 | Shoe Flower / China Rose | *Hibiscus rosa-sinensis* L. |
| Honge | PLANT_017 | Indian Beech Tree | *Millettia pinnata* (L.) Panigrahi |
| Insulin | PLANT_018 | Insulin Plant | *Chamaecostus cuspidatus* |
| Jasmine | PLANT_019 | Jasmine | *Jasminum officinale* L. |
| Lemon | PLANT_020 | Lemon | *Citrus limon* (L.) Osbeck |
| Lemon_grass | PLANT_021 | Lemongrass | *Cymbopogon citratus* (DC.) Stapf |
| Mango | PLANT_022 | Mango Tree | *Mangifera indica* L. |
| Mint | PLANT_023 | Mint / Spearmint | *Mentha spicata* L. |
| Nagadali | PLANT_024 | Common Rue / Herb-of-Grace | *Ruta graveolens* L. |
| Neem | PLANT_025 | Neem Tree / Indian Lilac | *Azadirachta indica* A.Juss. |
| Nithyapushpa | PLANT_026 | Madagascar Periwinkle | *Catharanthus roseus* (L.) G.Don |
| Nooni | PLANT_027 | Noni / Indian Mulberry | *Morinda citrifolia* L. |
| Pappaya | PLANT_028 | Papaya Tree | *Carica papaya* L. |
| Pepper | PLANT_029 | Black Pepper | *Piper nigrum* L. |
| Pomegranate | PLANT_030 | Pomegranate | *Punica granatum* L. |
| Raktachandini | PLANT_031 | Red Sandalwood | *Pterocarpus santalinus* L.f. |
| Rose | PLANT_032 | Rose | *Rosa indica* L. |
| Tulasi | PLANT_033 | Holy Basil | *Ocimum sanctum* L. |
| Wood_sorel | PLANT_034 | Indian Sorrel / Creeping Wood Sorrel | *Oxalis corniculata* L. |

---

## 🏗️ Architecture

```
ai-service/
├── app/
│   ├── main.py                          # FastAPI application entry point
│   ├── pipeline.py                      # Orchestrates quality → predict → fusion → response
│   ├── api/
│   │   └── routes.py                    # POST /ai/analyze endpoint
│   ├── models/
│   │   ├── classifier.py                # EfficientNetV2-S model wrapper
│   │   ├── embeddings.py                # Feature embedding extractor
│   │   └── plant_registry.py            # Plant ID ↔ class name mapping
│   ├── inference/
│   │   ├── predictor.py                 # Top-k prediction with class mapping
│   │   ├── confidence.py                # Softmax confidence calculator
│   │   └── unknown_detector.py          # Known/Unknown decision (threshold=0.60)
│   ├── preprocessing/
│   │   ├── image_preprocessor.py        # Resize, normalize for EfficientNet
│   │   └── quality_checker.py           # Blur, brightness, resolution checks
│   ├── explainability/
│   │   └── gradcam.py                   # Grad-CAM heatmap generator
│   ├── multi_image/
│   │   └── evidence_fusion.py           # Weighted consensus across images
│   └── schemas/
│       ├── response.py                  # Pydantic response models
│       └── response_builder.py          # Internal → API response mapping
├── artifacts/models/
│   ├── efficientnet_v2_s.pth            # Trained model weights
│   └── class_mapping.json               # Index → class name
├── tests/                               # 78 unit tests
├── Dockerfile                           # Production Docker image
├── modal_app.py                         # Modal.com deployment
└── requirements.txt                     # Python dependencies
```

### Pipeline Flow

```
  Upload Images
       │
       ▼
  ┌──────────────┐
  │ Quality Check │  ← blur, brightness, resolution
  │  (per image)  │
  └──────┬───────┘
         │ acceptable?
    ┌────┴────┐
    No       Yes
    │         │
    ▼         ▼
  REJECT  ┌──────────────┐
          │  EfficientNet │  ← forward pass
          │  Prediction   │
          └──────┬───────┘
                 │
                 ▼
          ┌──────────────┐
          │  Grad-CAM    │  ← visual explanation
          │  Heatmap     │
          └──────┬───────┘
                 │
                 ▼
          ┌──────────────┐
          │  Unknown     │  ← confidence ≥ 0.60?
          │  Detector    │
          └──────┬───────┘
                 │
          (multi-image?)
            ┌────┴────┐
            No       Yes
            │         │
            ▼         ▼
         Single  ┌──────────────┐
         Result  │  Evidence    │  ← weighted vote
                 │  Fusion      │     agreement ≥ 80%
                 └──────┬───────┘
                        │
                        ▼
                 ┌──────────────┐
                 │  Registry    │  ← class → plant_id
                 │  Lookup      │
                 └──────┬───────┘
                        │
                        ▼
                   JSON Response
```

---

## 🛠️ Local Development

### Prerequisites

- Python 3.11+
- Trained model weights at `artifacts/models/efficientnet_v2_s.pth`

### Setup

```bash
cd ai-service
python -m venv .venv311
.venv311\Scripts\activate      # Windows
# source .venv311/bin/activate  # Linux/Mac

pip install -r requirements.txt
```

### Run Server

```bash
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Open [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs) for the Swagger UI.

### Run Tests

```bash
python -m pytest tests -v
# 78 passed ✅
```

---

## 🐳 Docker

```bash
# From repo root
docker build -f ai-service/Dockerfile -t dravyasetu-ai .
docker run -p 8000:8000 dravyasetu-ai
```

---

## ☁️ Deployment

### Modal (Current — Free Tier)

```bash
cd ai-service
pip install modal
modal setup
modal deploy modal_app.py
```

---

## 📊 Model Details

| Property | Value |
|----------|-------|
| Architecture | EfficientNetV2-S |
| Input Size | 300 × 300 px |
| Classes | 34 |
| Training Data | 1,492 curated iNaturalist images |
| Confidence Threshold | 0.60 |
| Fusion Agreement | ≥ 80% weighted consensus |
| Quality Min Resolution | 224 × 224 px |
| Explainability | Grad-CAM (base64 JPEG) |

---

## 📄 License

This project is part of the DravyaSetu initiative for Ayurvedic plant identification research.

---

<p align="center">
  Built with ❤️ for Ayurveda
</p>
