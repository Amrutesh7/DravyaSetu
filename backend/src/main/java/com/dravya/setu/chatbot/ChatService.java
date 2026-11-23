package com.dravya.setu.chatbot;

import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.common.LanguageService;
import com.dravya.setu.knowledge.KnowledgeClient;
import com.dravya.setu.knowledge.KnowledgeRequest;
import com.dravya.setu.knowledge.KnowledgeResponse;
import com.dravya.setu.knowledge.KnowledgeServiceException;
import com.dravya.setu.knowledge.KnowledgeSource;
import com.dravya.setu.knowledge.VoiceNotSupportedException;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.users.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
public class ChatService {

    private static final Set<String> SUPPORTED_AUDIO_TYPES =
            Set.of("audio/mpeg", "audio/wav", "audio/ogg", "audio/webm");

    private static final int MAX_CONTEXT_LINE_CHARS = 400;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final KnowledgeClient knowledgeClient;
    private final LanguageService languageService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PlantRepository plantRepository;
    private final ChatPersistenceService persistence;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final boolean historyEnabled;
    private final int historyMessageLimit;
    private final int historyCharLimit;

    @org.springframework.beans.factory.annotation.Autowired
    public ChatService(
            KnowledgeClient knowledgeClient, LanguageService languageService,
            ChatSessionRepository chatSessionRepository, ChatMessageRepository chatMessageRepository,
            PlantRepository plantRepository,
            @Value("${app.chat.history-enabled:true}") boolean historyEnabled,
            @Value("${app.chat.history-message-limit:6}") int historyMessageLimit,
            @Value("${app.chat.history-char-limit:2000}") int historyCharLimit,
            ChatPersistenceService persistence) {

        this.knowledgeClient = knowledgeClient;
        this.languageService = languageService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.plantRepository = plantRepository;
        this.persistence = persistence;
        this.historyEnabled = historyEnabled;
        this.historyMessageLimit = historyMessageLimit;
        this.historyCharLimit = historyCharLimit;
    }

    ChatService(KnowledgeClient client, LanguageService languages, ChatSessionRepository sessions,
                ChatMessageRepository messages, PlantRepository plants, boolean enabled, int limit, int chars) {
        this(client, languages, sessions, messages, plants, enabled, limit, chars,
                new ChatPersistenceService(sessions, messages));
    }

    /** Convenience constructor that does not persist state; intended for tests. */
    ChatService(KnowledgeClient knowledgeClient, LanguageService languageService) {
        this(knowledgeClient, languageService, null, null, null, false, 0, 0);
    }

    public ChatResponse ask(ChatRequest request) {
        return ask(request, UUID.randomUUID().toString());
    }

    public ChatResponse ask(ChatRequest request, String requestId) {
        return ask(request, null, requestId);
    }

    public ChatResponse ask(ChatRequest request, User user, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        // 1. Validate application request
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (request.getMessage().length() > 4_000) {
            throw new IllegalArgumentException("message must be at most 4000 characters");
        }

        String message = request.getMessage().trim();
        String plantCode = blankToNull(request.getPlantId());
        String language = normaliseLanguage(request.getLanguage());
        languageService.requireSupported(language);

        boolean persist = user != null && chatSessionRepository != null;

        // 2. Resolve the application session up front so its id can be sent to Person 3.
        ChatSession existingSession = null;
        Long plantDatabaseId = null;

        if (persist) {
            if (request.getSessionId() != null) {
                existingSession = chatSessionRepository
                        .findByIdAndUserId(request.getSessionId(), user.getId())
                        .orElseThrow(() -> ClientErrorException.notFound("Chat session not found"));

                if (plantCode == null && existingSession.getPlantId() != null) {
                    plantCode = plantRepository.findById(existingSession.getPlantId())
                            .map(plant -> plant.getPlantCode())
                            .orElse(null);
                }
            }

            if (plantCode != null) {
                // Resolve before calling Person 3 so an unknown plant does not cost a slow LLM call.
                plantDatabaseId = resolvePlantDatabaseId(plantCode);
            }
        }

        UUID sessionId = existingSession != null ? existingSession.getId()
                : request.getSessionId() != null ? request.getSessionId()
                : UUID.randomUUID();

        /*
         * 3. Person 3's deployed service keeps no session state, so earlier turns are rebuilt
         * here and prepended to the outbound message. Only a session that already exists can
         * have history; a brand new session cannot.
         */
        String knowledgeMessage = existingSession == null
                ? message
                : withConversationContext(message, sessionId);

        // 4. Build and send the knowledge request using Person 3's deployed contract.
        KnowledgeRequest knowledgeRequest = new KnowledgeRequest(
                requestId,
                sessionId.toString(),
                knowledgeMessage,
                language,
                language,
                plantCode,
                request.isGenerateAudio()
        );

        KnowledgeResponse knowledgeResponse = knowledgeClient.ask(knowledgeRequest);

        // 5. Validate the knowledge response.
        validateKnowledgeResponse(knowledgeResponse);

        // 6. Construct the public response.
        ChatResponse chatResponse = new ChatResponse(
                knowledgeResponse.getRequestId(),
                sessionId,
                knowledgeResponse.getDetectedLanguage(),
                knowledgeResponse.getTranscript(),
                knowledgeResponse.getAnswer(),
                knowledgeResponse.getLanguage(),
                sourcesOrEmpty(knowledgeResponse),
                knowledgeResponse.getVoiceOutput(),
                warningsOrEmpty(knowledgeResponse)
        );

        if (persist) {
            // Store the caller's original message, never the context-augmented prompt.
            persistConversation(existingSession, sessionId, user, plantDatabaseId, language, message, chatResponse);
        }

        return chatResponse;
    }

    /**
     * The deployed knowledge service does not use {@code session_id} for memory, and ignores an
     * unknown {@code conversation_context} field. Earlier turns are therefore folded into the
     * outbound message, most recent first within a fixed budget.
     */
    private String withConversationContext(String message, UUID sessionId) {

        if (!historyEnabled || chatMessageRepository == null
                || historyMessageLimit <= 0 || historyCharLimit <= 0) {
            return message;
        }

        List<ChatMessage> recent = new ArrayList<>(
                chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                        sessionId, PageRequest.of(0, historyMessageLimit)));

        if (recent.isEmpty()) {
            return message;
        }

        Collections.reverse(recent);

        Deque<String> lines = new ArrayDeque<>();
        int budget = historyCharLimit;

        for (int index = recent.size() - 1; index >= 0; index--) {
            ChatMessage item = recent.get(index);
            String line = roleLabel(item.getRole()) + ": " + compact(item.getContent());

            if (line.length() > budget) {
                break;
            }

            lines.addFirst(line);
            budget -= line.length() + 1;
        }

        if (lines.isEmpty()) {
            return message;
        }

        return "Earlier conversation (context only, not part of the current question):\n"
                + String.join("\n", lines)
                + "\n\nCurrent question: " + message;
    }

    private void persistConversation(ChatSession existingSession, UUID sessionId, User user,
            Long plantDatabaseId, String language, String message, ChatResponse response) {
        String sourcesJson = objectMapper.writeValueAsString(response.getSources());
        persistence.save(sessionId, existingSession != null, user.getId(), plantDatabaseId,
                language, message, response.getAnswer(), sourcesJson);
        response.setSessionId(sessionId);
    }

    // ---------------------------------------------------------------- chat history

    public Page<ChatSessionSummary> getSessions(User user, int page, int size) {
        requirePaging(page, size);
        requirePersistence();

        Map<Long, String> plantCodes = new HashMap<>();

        return chatSessionRepository
                .findByUserIdOrderByUpdatedAtDesc(user.getId(), PageRequest.of(page, size))
                .map(session -> toSummary(session, plantCodes));
    }

    public ChatSessionSummary getSession(UUID sessionId, User user) {
        requirePersistence();
        return toSummary(requireSession(sessionId, user), new HashMap<>());
    }

    public Page<ChatMessageResponse> getSessionMessages(UUID sessionId, User user, int page, int size) {
        requirePaging(page, size);
        requirePersistence();

        // Ownership check before reading messages.
        requireSession(sessionId, user);

        return chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId, PageRequest.of(page, size))
                .map(this::toMessageResponse);
    }

    private ChatSession requireSession(UUID sessionId, User user) {
        return chatSessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> ClientErrorException.notFound("Chat session not found"));
    }

    private ChatSessionSummary toSummary(ChatSession session, Map<Long, String> plantCodes) {
        return new ChatSessionSummary(
                session.getId(),
                resolvePlantCode(session.getPlantId(), plantCodes),
                session.getLanguage(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                roleLabel(message.getRole()),
                message.getContent(),
                parseSources(message.getSourcesJson()),
                message.getCreatedAt());
    }

    private String resolvePlantCode(Long plantDatabaseId, Map<Long, String> cache) {
        if (plantDatabaseId == null || plantRepository == null) {
            return null;
        }
        if (cache.containsKey(plantDatabaseId)) {
            return cache.get(plantDatabaseId);
        }
        String plantCode = plantRepository.findById(plantDatabaseId)
                .map(plant -> plant.getPlantCode())
                .orElse(null);
        cache.put(plantDatabaseId, plantCode);
        return plantCode;
    }

    private List<KnowledgeSource> parseSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return List.of();
        }
        try {
            List<KnowledgeSource> sources =
                    objectMapper.readValue(sourcesJson, new TypeReference<List<KnowledgeSource>>() { });
            return sources == null ? List.of() : sources;
        } catch (RuntimeException exception) {
            // A malformed legacy row must not break history reads.
            return List.of();
        }
    }

    private static void requirePaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_HISTORY_PAGE_SIZE) {
            throw ClientErrorException.badRequest(
                    "page must be non-negative and size must be between 1 and " + MAX_HISTORY_PAGE_SIZE);
        }
    }

    private void requirePersistence() {
        if (chatSessionRepository == null || chatMessageRepository == null) {
            throw ClientErrorException.notFound("Chat history is not available");
        }
    }

    /**
     * Voice <em>input</em> is not available: the deployed knowledge service only exposes
     * {@code POST /knowledge/chat} with {@code input_type="text"}.
     * Callers should send text and set {@code generate_audio=true} for a spoken answer.
     */
    public ChatResponse askVoice(VoiceChatRequest request) {
        return askVoice(request, UUID.randomUUID().toString());
    }

    public ChatResponse askVoice(VoiceChatRequest request, String requestId) {
        if (request == null || request.getAudio() == null || request.getAudio().isEmpty()) {
            throw new IllegalArgumentException("audio is required");
        }
        String contentType = request.getAudio().getContentType();
        if (contentType == null || !SUPPORTED_AUDIO_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("audio must be MP3, WAV, OGG, or WebM");
        }

        String language = normaliseLanguage(request.getLanguage());
        languageService.requireSupported(language);

        throw new VoiceNotSupportedException(
                "Voice input is not supported by the knowledge service; "
                        + "send text to POST /chat with generate_audio=true for a spoken answer"
        );
    }

    private void validateKnowledgeResponse(KnowledgeResponse response) {

        if (response == null) {
            throw new KnowledgeServiceException(
                    "Knowledge service returned an empty response"
            );
        }

        if (response.getRequestId() == null || response.getRequestId().isBlank()) {
            throw new KnowledgeServiceException(
                    "Knowledge service returned an invalid request_id"
            );
        }

        if (response.getAnswer() == null) {
            throw new KnowledgeServiceException(
                    "Knowledge service returned a null answer"
            );
        }

        if (response.getLanguage() == null || response.getLanguage().isBlank()) {
            throw new KnowledgeServiceException(
                    "Knowledge service returned an invalid language"
            );
        }

        /*
         * detected_language, transcript, sources and warnings are required by Person 3's
         * published contract, but a missing list should not discard a usable answer, so
         * nulls are normalised instead of rejected.
         */
    }

    private Long resolvePlantDatabaseId(String plantCode) {
        return plantRepository.findByPlantCode(plantCode)
                .map(plant -> plant.getId())
                .orElseThrow(() -> ClientErrorException.badRequest("Unknown plant_id: " + plantCode));
    }

    private static List<KnowledgeSource> sourcesOrEmpty(KnowledgeResponse response) {
        return response.getSources() == null ? List.of() : response.getSources();
    }

    private static List<String> warningsOrEmpty(KnowledgeResponse response) {
        return response.getWarnings() == null ? List.of() : response.getWarnings();
    }

    private static String roleLabel(String role) {
        return "ASSISTANT".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private static String compact(String content) {
        if (content == null) {
            return "";
        }
        String flattened = content.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_CONTEXT_LINE_CHARS
                ? flattened
                : flattened.substring(0, MAX_CONTEXT_LINE_CHARS) + "…";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normaliseLanguage(String language) {
        return language == null || language.isBlank() ? "en" : language.trim();
    }
}
