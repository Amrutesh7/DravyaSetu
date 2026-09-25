import torch

from app.models.classifier import create_model


print("Creating EfficientNetV2 model...")

model = create_model()

print("Model created successfully.")

print("Number of classes:", 34)

# Create a fake image tensor
# Shape = [batch, channels, height, width]
dummy_input = torch.randn(1, 3, 224, 224)

print("Input shape:", dummy_input.shape)

# Run the image through the model
with torch.no_grad():
    output = model(dummy_input)

print("Output shape:", output.shape)

