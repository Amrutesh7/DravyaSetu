package com.dravya.setu.chatbot;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> ask(
            @RequestBody ChatRequest request) {

        ChatResponse response =
                chatService.ask(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
}