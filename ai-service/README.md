# 🌿 DravyaSetu — AI & Computer Vision Module

> **Person 1 — AI/ML & Computer Vision**

The **AI module of DravyaSetu** is responsible for identifying medicinal plants from images using Computer Vision and Deep Learning. It forms the intelligence layer of the platform, converting plant images into meaningful predictions that can be consumed by the backend and frontend.

The module is designed to support **single-image identification, multi-image evidence, confidence estimation, unknown-plant detection, visual explainability, and future integration with the Digital Plant Passport**.

---

## 🎯 My Role in DravyaSetu

As **Person 1**, I am responsible for designing and implementing the AI/ML pipeline of DravyaSetu.

### Core responsibilities

* 📊 Medicinal plant dataset preparation and curation
* 🧹 Dataset cleaning and duplicate detection
* 🌿 Plant-class organization and balancing
* 🖼️ Image preprocessing and quality checking
* 🧠 Deep Learning model development
* 🔬 Computer Vision based plant identification
* 📈 Model training and evaluation
* 🎯 Confidence estimation
* ❓ Unknown plant detection
* 🔍 Image similarity and embedding search
* 🧩 Multi-image evidence fusion
* 🔥 Grad-CAM based explainability
* 🚀 FastAPI inference service
* 🔗 Integration with the overall DravyaSetu architecture

---

# 🧠 AI Pipeline

The overall AI pipeline is designed as:

```text
                 Plant Image
                     │
                     ▼
             Image Quality Check
                     │
                     ▼
              Preprocessing
                     │
                     ▼
          EfficientNetV2-S Model
                     │
                     ▼
          Feature Extraction
                     │
                     ▼
             Classification
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
   Confidence Score       Unknown Detection
          │                     │
          └──────────┬──────────┘
                     ▼
             Final Prediction
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
      Grad-CAM              Embeddings
          │                     │
          ▼                     ▼
   Explainability          Similarity Search
                     │
                     ▼
               API Response
```

---

# 🌱 Dataset

For the initial Review 1 model, a curated medicinal plant dataset containing **6,017 images across 34 classes** has been prepared.

### Classes

```text
Aloe_Vera
Amla
Amruta_Balli
Arali
Ashoka
Ashwagandha
Betel
Betel_Nut
Brahmi
Castor
Curry_Leaf
Doddapatre
Ekka
Ganike
Henna
Hibiscus
Honge
Insulin
Jasmine
Lemon
Lemon_grass
Mango
Mint
Nagadali
Neem
Nithyapushpa
Nooni
Pappaya
Pepper
Pomegranate
Raktachandini
Rose
Tulasi
Wood_sorel
```

### Dataset distribution

| Split      |    Images |
| ---------- | --------: |
| Training   |     4,793 |
| Validation |       612 |
| Testing    |       612 |
| **Total**  | **6,017** |

The dataset was cleaned and balanced to provide approximately equal representation for the 34 target classes.

---

# 🧹 Dataset Curation

A significant part of the AI work involves preparing reliable training data rather than directly training on raw images.

The dataset pipeline included:

### 1. Class selection

A fixed set of 34 medicinal plant classes was selected for the initial classifier.

### 2. Dataset cleaning

Images were inspected and organized into class-specific directories.

### 3. Duplicate detection

Duplicate images were identified using:

* SHA-based exact duplicate detection
* Perceptual hashing for visually similar images

This helped reduce the possibility of the same image appearing multiple times in the training data.

### 4. Dataset balancing

The candidate pool was balanced to approximately **177 images per class**.

One class, `Lemon_grass`, contains 176 usable images, resulting in the final dataset size of 6,017.

### 5. Train/Validation/Test split

The dataset was divided into:

```text
Train       → 4,793 images
Validation  →   612 images
Test        →   612 images
```

The test set remains separate from training so that the final model can be evaluated on unseen images.

---

# 🧠 Deep Learning Model

The initial Review 1 model uses:

## EfficientNetV2-S

The model is used through **transfer learning**, using a pretrained vision backbone and adapting its final classification layer to the 34 medicinal plant classes.

```text
Pretrained EfficientNetV2-S
            │
            ▼
       Feature Extraction
            │
            ▼
      Classification Head
            │
            ▼
       34 Plant Classes
```

### Why EfficientNetV2-S?

EfficientNetV2-S provides a strong balance between:

* Classification performance
* Model size
* Training efficiency
* GPU compatibility
* Transfer-learning capability

Training is performed using a **Google Colab GPU**, currently using a **Tesla T4**.

---

# 🖼️ Image Preprocessing

Before an image reaches the classifier, it passes through the preprocessing pipeline.

The preprocessing layer is responsible for preparing images into the format expected by the model.

Typical operations include:

```text
Input Image
     ↓
Image Loading
     ↓
Resize
     ↓
Normalization
     ↓
Tensor Conversion
     ↓
Model
```

The preprocessing module is implemented under:

```text
app/preprocessing/
├── image_preprocessor.py
└── quality_checker.py
```

---

# 🎯 Prediction & Confidence

The inference system is designed to return more than just a plant name.

A prediction can contain:

```json
{
  "plant": "Tulasi",
  "confidence": 0.94
}
```

The confidence module is responsible for interpreting the model's prediction probability.

Located at:

```text
app/inference/confidence.py
```

---

# ❓ Unknown Plant Detection

A major requirement of the DravyaSetu AI system is avoiding blind classification.

A normal classifier will always attempt to select one of its known classes, even when the input image does not belong to any of them.

DravyaSetu therefore includes an **unknown detection layer**.

Conceptually:

```text
Input Image
     ↓
Classifier
     ↓
Is confidence reliable?
     │
 ┌───┴────┐
 │        │
 YES      NO
 │        │
 ▼        ▼
Known    Unknown
Plant    Plant
```

Implementation:

```text
app/inference/unknown_detector.py
```

This allows the system to communicate uncertainty rather than presenting every image as a valid medicinal plant prediction.

---

# 🔍 Image Embeddings & Similarity Search

The AI service also supports extracting image embeddings.

Instead of representing an image only as a class label, the model can represent it as a numerical feature vector.

```text
Plant Image
     ↓
Feature Extractor
     ↓
Embedding Vector
     ↓
Similarity Search
     ↓
Most Similar Plant Images
```

Relevant modules:

```text
app/models/embeddings.py
app/similarity/faiss_search.py
```

FAISS can be used to efficiently search large collections of image embeddings.

This can support future capabilities such as:

* Similar plant images
* Visual evidence retrieval
* Supporting classification decisions
* Plant knowledge retrieval
* Multi-image evidence

---

# 🧩 Multi-Image Evidence Fusion

A plant may be difficult to identify from a single image.

For example, different images may show:

```text
Image 1 → Leaf
Image 2 → Flower
Image 3 → Stem
Image 4 → Fruit
```

Instead of treating each image independently, DravyaSetu can combine evidence from multiple images.

```text
          Leaf Image
              │
          Flower Image
              │
          Stem Image
              │
          Fruit Image
              │
              ▼
       Evidence Fusion
              │
              ▼
      Final Plant Prediction
```

Implementation:

```text
app/multi_image/evidence_fusion.py
```

This is intended to improve robustness when multiple parts of the same plant are available.

---

# 🔥 Explainable AI

The AI module also includes **Grad-CAM based explainability**.

The purpose is to help visualize which regions of the image influenced the model's prediction.

```text
Original Image
      ↓
Model Prediction
      ↓
Grad-CAM
      ↓
Activation Heatmap
      ↓
Important Image Regions
```

Implementation:

```text
app/explainability/gradcam.py
```

This is particularly useful for demonstrating that the model is focusing on meaningful plant characteristics rather than irrelevant background regions.

---

# 🚀 FastAPI AI Service

The trained model is exposed through a FastAPI service.

Project structure:

```text
ai-service/
│
├── app/
│   ├── main.py
│   │
│   ├── api/
│   │   └── routes.py
│   │
│   ├── explainability/
│   │   └── gradcam.py
│   │
│   ├── inference/
│   │   ├── confidence.py
│   │   ├── predictor.py
│   │   └── unknown_detector.py
│   │
│   ├── models/
│   │   ├── classifier.py
│   │   └── embeddings.py
│   │
│   ├── multi_image/
│   │   └── evidence_fusion.py
│   │
│   ├── preprocessing/
│   │   ├── image_preprocessor.py
│   │   └── quality_checker.py
│   │
│   ├── schemas/
│   │   └── response.py
│   │
│   └── similarity/
│       └── faiss_search.py
│
├── artifacts/
│   ├── models/
│   ├── embeddings/
│   └── indexes/
│
├── configs/
├── data/
├── notebooks/
├── scripts/
├── tests/
│
├── Dockerfile
├── requirements.txt
└── README.md
```

---

# 🔗 API Architecture

The intended flow is:

```text
Frontend
   │
   │ Plant Image
   ▼
FastAPI AI Service
   │
   ▼
Preprocessing
   │
   ▼
EfficientNetV2-S
   │
   ├── Prediction
   ├── Confidence
   ├── Unknown Detection
   ├── Embedding
   └── Explainability
   │
   ▼
JSON Response
   │
   ▼
Frontend / Backend
```

Potential endpoints include:

```text
GET  /health
POST /predict
POST /predict/multi-image
```

The API will allow the AI model to operate independently from the frontend and backend services.

---

# 🐳 Deployment

The AI service is containerized using Docker.

```text
Docker
   ↓
FastAPI
   ↓
AI Model
   ↓
REST API
```

The deployment target can expose the service through a public HTTPS endpoint for integration with the rest of the DravyaSetu platform.

---

# 📈 Evaluation

The model will be evaluated using:

* Training accuracy
* Validation accuracy
* Test accuracy
* Precision
* Recall
* F1-score
* Confusion matrix
* Per-class performance

The confusion matrix will be particularly important because visually similar plants may be confused with one another.

---

# 🔮 Post-Review-1 Improvements

The Review 1 model is an initial baseline.

After Review 1, the dataset can be expanded by approximately **4,000–6,000 additional images**.

The expanded dataset can then be used for:

```text
Initial Model
     ↓
More Training Data
     ↓
More Visual Diversity
     ↓
Fine-tuning
     ↓
Improved Model
     ↓
Performance Comparison
```

Future improvements can include:

* More images per plant class
* More diverse backgrounds
* Different lighting conditions
* Different plant growth stages
* Leaf/flower/fruit/stem images
* Hard-negative samples
* Improved unknown detection
* Better confidence calibration
* More robust multi-image fusion

---

# 🛠️ Technology Stack

| Component         | Technology           |
| ----------------- | -------------------- |
| Programming       | Python               |
| Deep Learning     | PyTorch              |
| Computer Vision   | Torchvision / OpenCV |
| Model             | EfficientNetV2-S     |
| Dataset Training  | Google Colab         |
| GPU               | NVIDIA Tesla T4      |
| API               | FastAPI              |
| Similarity Search | FAISS                |
| Explainability    | Grad-CAM             |
| Containerization  | Docker               |
| Version Control   | Git / GitHub         |

---

# 📁 AI Service Responsibilities

The AI module is organized around the following responsibilities:

```text
Dataset
   └── Data collection, cleaning, balancing

Preprocessing
   └── Image preparation & quality checks

Models
   └── EfficientNetV2-S & embeddings

Inference
   └── Prediction, confidence & unknown detection

Similarity
   └── FAISS image similarity

Explainability
   └── Grad-CAM

Multi-Image
   └── Evidence fusion

API
   └── FastAPI endpoints

Artifacts
   └── Trained models, embeddings & indexes
```

---

# 🎯 Final Goal

The goal of this module is not simply to classify an image.

The AI system is intended to provide a complete **plant intelligence pipeline**:

```text
              🌿 Plant Image
                    │
                    ▼
             Image Analysis
                    │
          ┌─────────┼─────────┐
          ▼         ▼         ▼
      Quality   Classification  Embedding
          │         │         │
          │         ▼         ▼
          │     Confidence   Similarity
          │         │
          └────┬────┘
               ▼
        Unknown Detection
               │
               ▼
       Explainable Prediction
               │
               ▼
        Multi-image Evidence
               │
               ▼
          AI API Response
```

This AI service acts as the **computer vision and intelligence layer of DravyaSetu**, providing the foundation for reliable medicinal plant identification and future plant-intelligence features.
