package com.dravya.setu.chatbot;

import com.dravya.setu.common.RequestIdFilter;
import com.dravya.setu.users.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> ask(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) {

        ChatResponse response =
                chatService.ask(request, user, requestId(httpRequest));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping(value = "/voice", consumes = "multipart/form-data")
    public ResponseEntity<ChatResponse> askVoice(
            @RequestPart("audio") MultipartFile audio,
            @RequestPart(value = "plant_id", required = false) String plantId,
            @RequestPart(value = "language", required = false) String language,
            HttpServletRequest httpRequest) {
        VoiceChatRequest request = new VoiceChatRequest();
        request.setAudio(audio);
        request.setPlantId(plantId);
        request.setLanguage(language);
        return ResponseEntity.ok(chatService.askVoice(request, requestId(httpRequest)));
    }

    /** Sessions owned by the caller, newest activity first. */
    @GetMapping("/sessions")
    public Page<ChatSessionSummary> sessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {

        return chatService.getSessions(user, page, size);
    }

    /** A single caller-owned session. */
    @GetMapping("/sessions/{sessionId}")
    public ChatSessionSummary session(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal User user) {

        return chatService.getSession(sessionId, user);
    }

    /** Persisted messages of a caller-owned session, oldest first. */
    @GetMapping("/sessions/{sessionId}/messages")
    public Page<ChatMessageResponse> sessionMessages(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal User user) {

        return chatService.getSessionMessages(sessionId, user, page, size);
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_HEADER);
    }
}
