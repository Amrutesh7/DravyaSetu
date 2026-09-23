# DravyaSetu Data Flow

## Plant identification
1. Web/Mobile uploads one or more plant images to Spring Boot.
2. Spring Boot authenticates and validates the request.
3. Spring Boot sends the image data, request ID and plant-part metadata to Person 1 AI.
4. Person 1 performs image quality screening, classification, reliability/unknown handling and other visual analysis.
5. Person 1 returns the canonical AI response.
6. Spring Boot validates and maps that response to the public identification response.
7. Spring Boot stores application identification data and returns the result to Web/Mobile.

## Plant knowledge
1. Web/Mobile sends a chat request to Spring Boot.
2. Spring Boot supplies the shared plant ID when plant context exists.
3. Spring Boot calls Person 3 Knowledge Service.
4. Person 3 retrieves grounded knowledge and generates the response.
5. Spring Boot returns the knowledge response to Web/Mobile.

## Voice
Web/Mobile → Spring Boot → Knowledge Service → STT → language processing/RAG/LLM → TTS → Spring Boot → Web/Mobile
