from PIL import Image
from app.preprocessing.image_preprocessor import preprocess_image


IMAGE_PATH = "test_plant.jpg"

image = Image.open(IMAGE_PATH)

print("Original image size:", image.size)
print("Original image mode:", image.mode)

tensor = preprocess_image(image)

print("Processed tensor shape:", tensor.shape)
print("Tensor type:", tensor.dtype)