from PIL import Image
from app.preprocessing.quality_checker import check_image_quality


IMAGE_PATH = "test_plant.jpg"

image = Image.open(IMAGE_PATH)

result = check_image_quality(image)

print("Image Quality Result:")
print(result)