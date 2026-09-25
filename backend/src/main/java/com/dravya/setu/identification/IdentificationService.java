package com.dravya.setu.identification;

import com.dravya.setu.ai.AiClient;
import com.dravya.setu.ai.AiRequest;
import com.dravya.setu.ai.AiResponse;
import com.dravya.setu.plants.Plant;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.users.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class IdentificationService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "SUCCESS",
            "LOW_CONFIDENCE",
            "UNKNOWN",
            "REJECTED",
            "PROCESSING",
            "FAILED"
    );

    private final IdentificationRepository identificationRepository;
    private final IdentificationImageRepository identificationImageRepository;
    private final PlantRepository plantRepository;
    private final AiClient aiClient;

    public IdentificationService(
            IdentificationRepository identificationRepository,
            IdentificationImageRepository identificationImageRepository,
            PlantRepository plantRepository,
            AiClient aiClient) {

        this.identificationRepository = identificationRepository;
        this.identificationImageRepository = identificationImageRepository;
        this.plantRepository = plantRepository;
        this.aiClient = aiClient;
    }

    @Transactional
    public IdentificationResponse createIdentification(
            IdentificationRequest request,
            User user) {

        // 1. Validate the request
        if (request == null ||
                request.getImageUrl() == null ||
                request.getImageUrl().isBlank()) {

            throw new IllegalArgumentException(
                    "imageUrl is required");
        }

        String imageUrl = request.getImageUrl().trim();

        // 2. Send the image reference to the AI service
        AiRequest aiRequest = new AiRequest(imageUrl);
        AiResponse aiResponse = aiClient.identify(aiRequest);

        // 3. Validate the AI response
        if (aiResponse == null) {
            throw new IllegalArgumentException(
                    "AI service returned an empty response");
        }

        if (aiResponse.getStatus() == null ||
                !VALID_STATUSES.contains(aiResponse.getStatus())) {

            throw new IllegalArgumentException(
                    "AI service returned an invalid status");
        }

        if (aiResponse.getConfidence() != null &&
                (aiResponse.getConfidence() < 0 ||
                        aiResponse.getConfidence() > 1)) {

            throw new IllegalArgumentException(
                    "AI service returned invalid confidence");
        }

        // SUCCESS and LOW_CONFIDENCE should identify a plant
        if (("SUCCESS".equals(aiResponse.getStatus()) ||
                "LOW_CONFIDENCE".equals(aiResponse.getStatus()))
                && (aiResponse.getPlantCode() == null ||
                aiResponse.getPlantCode().isBlank())) {

            throw new IllegalArgumentException(
                    "AI service did not provide plantCode");
        }

        // 4. Convert shared plantCode → internal plants.id
        Long plantId = null;

        if (aiResponse.getPlantCode() != null &&
                !aiResponse.getPlantCode().isBlank()) {

            Plant plant = plantRepository
                    .findByPlantCode(aiResponse.getPlantCode())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Plant not found for plantCode: "
                                            + aiResponse.getPlantCode()));

            plantId = plant.getId();
        }

        // 5. Create identification record
        Identification identification = new Identification();

        identification.setUserId(user.getId());
        identification.setPlantId(plantId);
        identification.setStatus(aiResponse.getStatus());

        if (aiResponse.getConfidence() != null) {
            identification.setConfidence(
                    BigDecimal.valueOf(aiResponse.getConfidence()));
        }

        identification.setModelVersion(
                aiResponse.getModelVersion());

        identification = identificationRepository.save(
                identification);

        // 6. Save image metadata
        IdentificationImage image =
                new IdentificationImage();

        image.setIdentificationId(
                identification.getId());

        image.setImageReference(imageUrl);

        image.setPlantPart(request.getPlantPart());

        image.setSequenceNumber(1);

        identificationImageRepository.save(image);

        // 7. Return API response
        return new IdentificationResponse(
                identification.getId(),
                aiResponse.getPlantCode(),
                aiResponse.getStatus(),
                aiResponse.getConfidence(),
                aiResponse.getModelVersion()
        );
    }

    // Existing ownership method from Phase 7
    public Identification getIdentificationForUser(
            Long identificationId,
            User user) {

        Identification identification =
                identificationRepository.findById(identificationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Identification not found"));

        if ("ADMIN".equals(user.getRole())) {
            return identification;
        }

        if (!identification.getUserId().equals(user.getId())) {
            throw new AccessDeniedException(
                    "You do not have access to this identification");
        }

        return identification;
    }
}