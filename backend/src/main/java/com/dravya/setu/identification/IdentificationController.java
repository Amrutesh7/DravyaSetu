package com.dravya.setu.identification;

import com.dravya.setu.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/identifications")
public class IdentificationController {

    private final IdentificationService identificationService;

    public IdentificationController(
            IdentificationService identificationService) {
        this.identificationService = identificationService;
    }

    @PostMapping
    public ResponseEntity<IdentificationResponse> createIdentification(
            @RequestBody IdentificationRequest request,
            @AuthenticationPrincipal User user) {

        IdentificationResponse response =
                identificationService.createIdentification(
                        request,
                        user
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}