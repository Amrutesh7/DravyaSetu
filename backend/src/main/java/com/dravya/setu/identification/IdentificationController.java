package com.dravya.setu.identification;

import com.dravya.setu.users.User;
import com.dravya.setu.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;

import java.util.List;

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
            @RequestPart("image") List<MultipartFile> images,
            @RequestPart(value = "plantPart", required = false) String plantPart,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) {

        IdentificationRequest request = new IdentificationRequest();
        request.setImages(images);
        request.setPlantPart(plantPart);

        String idempotencyKey = (String) httpRequest.getAttribute(
                RequestIdFilter.IDEMPOTENCY_KEY_ATTRIBUTE);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = (String) httpRequest.getAttribute(RequestIdFilter.REQUEST_ID_HEADER);
        }

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

    @GetMapping("/{id}")
    public IdentificationHistoryResponse getIdentification(@PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return identificationService.getHistoryItem(id, user);
    }

    @GetMapping
    public Page<IdentificationHistoryResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort,
            @AuthenticationPrincipal User user) {
        return identificationService.getHistory(user, page, size, sort);
    }
}
