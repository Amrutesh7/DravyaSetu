package com.dravya.setu.chatbot;

import com.dravya.setu.common.LanguageService;
import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.knowledge.MockKnowledgeClient;
import com.dravya.setu.knowledge.VoiceNotSupportedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {
    private final ChatService service = new ChatService(new MockKnowledgeClient(), new LanguageService());

    @Test
    void forwardsPlantMessageLanguageAndRequestIdToKnowledgeClient() {
        ChatRequest request = new ChatRequest();
        request.setPlantId("PLANT_001");
        request.setMessage("What is this plant?");
        request.setLanguage("kn");

        ChatResponse response = service.ask(request, "chat-123");

        assertEquals("chat-123", response.getRequestId());
        assertEquals("kn", response.getLanguage());
        assertEquals("kn", response.getDetectedLanguage());
        assertNull(response.getTranscript());
        assertEquals("This is a temporary knowledge-service response for PLANT_001.", response.getAnswer());
        assertNotNull(response.getSessionId());
        assertEquals(1, response.getSources().size());
        assertEquals("SRC_MOCK_001", response.getSources().get(0).getSourceId());
        assertNull(response.getVoiceOutput());
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    void reusesTheCallerSessionIdAsTheKnowledgeSessionId() {
        ChatRequest request = new ChatRequest();
        request.setMessage("What is this plant?");
        request.setLanguage("en");
        request.setSessionId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        ChatResponse response = service.ask(request, "chat-456");

        assertEquals(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                response.getSessionId()
        );
    }

    @Test
    void returnsVoiceMetadataWhenAudioOutputIsRequested() {
        ChatRequest request = new ChatRequest();
        request.setMessage("What is this plant?");
        request.setLanguage("en");
        request.setGenerateAudio(true);

        ChatResponse response = service.ask(request, "chat-789");

        assertNotNull(response.getVoiceOutput());
        assertEquals(false, response.getVoiceOutput().get("available"));
        assertEquals(1, response.getWarnings().size());
    }

    @Test
    void rejectsBlankLongAndUnsupportedLanguageMessages() {
        ChatRequest blank = new ChatRequest(); blank.setMessage("  ");
        assertThrows(IllegalArgumentException.class, () -> service.ask(blank));

        ChatRequest unsupported = new ChatRequest(); unsupported.setMessage("Hello"); unsupported.setLanguage("zz");
        assertThrows(ClientErrorException.class, () -> service.ask(unsupported));

        ChatRequest tooLong = new ChatRequest(); tooLong.setMessage("x".repeat(4_001));
        assertThrows(IllegalArgumentException.class, () -> service.ask(tooLong));
    }

    @Test
    void rejectsUnsupportedAudioAndReportsThatVoiceInputIsNotSupported() {
        VoiceChatRequest accepted = new VoiceChatRequest();
        accepted.setAudio(new MockMultipartFile("audio", "question.wav", "audio/wav", new byte[] {1, 2}));
        accepted.setLanguage("en");
        assertThrows(VoiceNotSupportedException.class, () -> service.askVoice(accepted, "voice-123"));

        VoiceChatRequest rejected = new VoiceChatRequest();
        rejected.setAudio(new MockMultipartFile("audio", "question.txt", "text/plain", new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> service.askVoice(rejected));
    }
}
