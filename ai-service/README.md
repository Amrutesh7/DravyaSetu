# 🌿 DravyaSetu — AI Service

> **AI and Computer Vision microservice for Ayurvedic medicinal plant identification.**  
> Built with EfficientNetV2-S, FastAPI, FAISS, and custom Grad-CAM explainability.

---

## 📌 Status

| Component | Status |
|---|---|
| Inference pipeline | ✅ Complete |
| Image preprocessing | ✅ Complete |
| Image quality gate | ✅ Complete |
| Grad-CAM explainability | ✅ Complete |
| Embedding extraction | ✅ Complete |
| FAISS similarity search | ✅ Complete |
| Unknown detection | ✅ Complete |
| REST API (`/ai/analyze`) | ✅ Complete |
| Response schema (Pydantic) | ✅ Complete |
| Dataset collection | 🔄 In progress |
| Model training | ⏳ Pending |
| FAISS index population | ⏳ Pending |
| End-to-end testing | ⏳ Pending |

---

## 🏗️ Architecture Overview

```
POST /ai/analyze (image upload)
         │
         ▼
┌─────────────────────┐
│   Image Validator   │  ← content-type check
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Image Quality Gate │  ← blur · brightness · size
└────────┬────────────┘
         │ rejected → return UNKNOWN + issues
         ▼
┌─────────────────────┐
│  Image Preprocessor │  ← resize 224×224 · ImageNet normalize
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  PlantPredictor     │  ← EfficientNetV2-S · softmax · top-3
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Unknown Detector   │  ← confidence < 0.50 → UNKNOWN
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  AIResponse (JSON)  │  ← Pydantic validated contract
└─────────────────────┘
```

---

## 📁 Project Structure

```
ai-service/
├── app/
│   ├── main.py                        # FastAPI app entry point
│   ├── api/
│   │   └── routes.py                  # POST /ai/analyze endpoint
│   ├── models/
│   │   ├── classifier.py              # EfficientNetV2-S model definition
│   │   ├── embeddings.py              # 1280-dim feature extractor
│   │   └── plant_registry.py          # Plant ID ↔ class name registry
│   ├── preprocessing/
│   │   ├── image_preprocessor.py      # Resize · normalize · batch
│   │   └── quality_checker.py         # Blur · brightness · size checks
│   ├── inference/
│   │   ├── predictor.py               # Top-k softmax inference
│   │   ├── confidence.py              # Confidence level classifier
│   │   └── unknown_detector.py        # KNOWN / UNKNOWN threshold logic
│   ├── explainability/
│   │   └── gradcam.py                 # Custom Grad-CAM (native PyTorch hooks)
│   ├── similarity/
│   │   └── faiss_search.py            # FAISS cosine similarity search
│   └── schemas/
│       └── response.py                # Pydantic AIResponse contract
├── requirements.txt
├── Dockerfile
└── README.md
```

---

## 🔌 API Reference

### `POST /ai/analyze`

Accepts a plant image and returns a full identification result.

**Request**
```
Content-Type: multipart/form-data
Body: image (file)
```

**Response**
```json
{
  "request_id": "uuid",
  "model_version": "efficientnetv2-s-dravyasetu-v1",
  "image_quality": {
    "acceptable": true,
    "issues": []
  },
  "identification": {
    "status": "KNOWN",
    "plant_id": "PLANT_001",
    "confidence": 0.94
  },
  "alternatives": [
    {
      "plant_id": "PLANT_007",
      "common_name": "Tulsi",
      "scientific_name": "Ocimum tenuiflorum",
      "confidence": 0.04
    }
  ],
  "similar_species": [],
  "explanation": {
    "method": "GRAD_CAM",
    "heatmap_url": null
  },
  "multi_image": {
    "used": false,
    "image_count": 1
  },
  "warnings": []
}
```

**Identification Status Values**

| Status | Meaning |
|---|---|
| `KNOWN` | Confidence ≥ 50% — plant identified |
| `UNKNOWN` | Confidence < 50% — not confident enough to identify |

---

## 🧠 Model

| Property | Value |
|---|---|
| Architecture | EfficientNetV2-S |
| Pretrained on | ImageNet |
| Fine-tuned for | 34 Ayurvedic medicinal plant species |
| Input size | 224 × 224 × 3 |
| Output | 34-class softmax probabilities |
| Embedding dim | 1280 (for similarity search) |
| Status | **Training pending** |

---

## 🔍 Image Quality Gate

Before inference, every uploaded image passes through an automated quality screening layer. Images that fail are rejected immediately with a descriptive error — preventing misleading predictions.

| Check | Threshold | Rejected if |
|---|---|---|
| Minimum size | 224 × 224 px | Image too small |
| Blur (Laplacian variance) | 50.0 | Score below threshold |
| Darkness (mean brightness) | 40.0 | Too dark |
| Overexposure (mean brightness) | 230.0 | Too bright |

---

## 🔦 Grad-CAM Explainability

This service includes a **custom Grad-CAM implementation** written entirely with native PyTorch forward/backward hooks — no external libraries required.

Once the model is trained, Grad-CAM will generate visual heatmaps showing *which region of a plant image* drove the prediction. This is critical for building user trust in a botanical identification context.

**Output images per request:**
- `gradcam_original.jpg` — original input image
- `gradcam_heatmap.jpg` — isolated activation heatmap (blue → cyan → yellow → red)
- `gradcam_overlay.jpg` — heatmap blended over the original (α = 0.45)

---

## ⚡ FAISS Similarity Search

Plant embeddings (1280-dim vectors from EfficientNetV2-S) are indexed using **FAISS with cosine similarity** (`IndexFlatIP` on L2-normalized vectors).

Once the model is trained and embeddings are generated from the dataset, the FAISS index will be populated to power the **"similar species"** feature — helping users distinguish between visually similar plants.

---

## 🚀 Getting Started

### Prerequisites
- Python 3.10+
- pip

### Installation

```bash
cd ai-service
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # Linux / macOS

pip install -r requirements.txt
```

### Run the Service

```bash
uvicorn app.main:app --reload --port 8001
```

The API will be available at:
- `http://localhost:8001/` — health check
- `http://localhost:8001/ai/analyze` — plant identification endpoint
- `http://localhost:8001/docs` — interactive Swagger UI

### Run with Docker

```bash
docker build -t dravyasetu-ai .
docker run -p 8001:8001 dravyasetu-ai
```

---

## 🧪 Tests

```bash
# Test preprocessing pipeline
python test_preprocessing.py

# Test image quality gate
python test_quality.py

# Test full model inference
python test_model.py
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Deep Learning | PyTorch · EfficientNetV2-S |
| Image Processing | Pillow · OpenCV · torchvision |
| Similarity Search | FAISS (Facebook AI) |
| API Framework | FastAPI · Uvicorn |
| Data Validation | Pydantic v2 |
| Explainability | Custom Grad-CAM (PyTorch hooks) |
| Containerization | Docker |

---

## 👤 Author

**Adithya S Nayak** — AI/ML Engineer  
*DravyaSetu Project · AI Service*
