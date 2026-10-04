package com.dravya.setu.chatbot;

import com.dravya.setu.knowledge.KnowledgeClient;
import com.dravya.setu.knowledge.KnowledgeRequest;
import com.dravya.setu.knowledge.KnowledgeResponse;
import com.dravya.setu.knowledge.KnowledgeServiceException;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final KnowledgeClient knowledgeClient;

    public ChatService(
            KnowledgeClient knowledgeClient) {

        this.knowledgeClient = knowledgeClient;
    }

    public ChatResponse ask(
            ChatRequest request) {

        // 1. Validate application request

        if (request == null ||
                request.getPlantCode() == null ||
                request.getPlantCode().isBlank()) {

            throw new IllegalArgumentException(
                    "plantCode is required"
            );
        }

        if (request.getQuestion() == null ||
                request.getQuestion().isBlank()) {

            throw new IllegalArgumentException(
                    "question is required"
            );
        }

        String plantCode =
                request.getPlantCode().trim();

        String question =
                request.getQuestion().trim();

        String language =
                request.getLanguage();

        if (language == null ||
                language.isBlank()) {

            language = "en";

        } else {

            language = language.trim();
        }

        // 2. Build Knowledge Service request

        KnowledgeRequest knowledgeRequest =
                new KnowledgeRequest(
                        plantCode,
                        question,
                        language
                );

        // 3. Call Knowledge Service

        KnowledgeResponse knowledgeResponse =
                knowledgeClient.ask(
                        knowledgeRequest
                );

        // 4. Validate Knowledge Service response

        validateKnowledgeResponse(
                knowledgeResponse
        );

        // 5. Construct application response

        return new ChatResponse(
                knowledgeResponse.getRequestId(),
                knowledgeResponse.getAnswer(),
                knowledgeResponse.getLanguage(),
                knowledgeResponse.getSources(),
                knowledgeResponse.getVoiceOutput(),
                knowledgeResponse.getWarnings(),
                knowledgeResponse.getErrors()
        );
    }

    private void validateKnowledgeResponse(
            KnowledgeResponse response) {

        if (response == null) {

            throw new KnowledgeServiceException(
                    "Knowledge service returned an empty response"
            );
        }

        if (response.getRequestId() == null ||
                response.getRequestId().isBlank()) {

            throw new KnowledgeServiceException(
                    "Knowledge service returned an invalid request_id"
            );
        }

        if (response.getAnswer() == null) {

            throw new KnowledgeServiceException(
                    "Knowledge service returned a null answer"
            );
        }

        if (response.getLanguage() == null ||
                response.getLanguage().isBlank()) {

            throw new KnowledgeServiceException(
                    "Knowledge service returned an invalid language"
            );
        }

        if (response.getSources() == null) {

            throw new KnowledgeServiceException(
                    "Knowledge service response is missing sources"
            );
        }

        if (response.getWarnings() == null) {

            throw new KnowledgeServiceException(
                    "Knowledge service response is missing warnings"
            );
        }

        /*
         * voice_output is optional and may be null.
         *
         * errors is also optional according to the supplied schema.
         * Therefore null is allowed for errors.
         */
    }
}
