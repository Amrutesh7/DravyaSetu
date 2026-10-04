package com.dravya.setu.identification;

import com.dravya.setu.users.User;
import com.dravya.setu.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/identifications")
public class IdentificationController {

    private final IdentificationService identificationService;

    public IdentificationController(
            IdentificationService identificationService) {
        this.identificationService = identificationService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<IdentificationResponse> createIdentification(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "plantPart", required = false) String plantPart,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) {

        IdentificationRequest request = new IdentificationRequest();
        request.setImage(image);
        request.setPlantPart(plantPart);

        String idempotencyKey = (String) httpRequest.getAttribute(
                RequestIdFilter.IDEMPOTENCY_KEY_ATTRIBUTE);

        IdentificationResponse response =
                identificationService.createIdentification(
                        request,
                        user,
                        idempotencyKey
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
