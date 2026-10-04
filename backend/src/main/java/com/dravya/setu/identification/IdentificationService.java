package com.dravya.setu.identification;

import com.dravya.setu.ai.AiClient;
import com.dravya.setu.ai.AiRequest;
import com.dravya.setu.ai.AiResponse;
import com.dravya.setu.ai.AiServiceException;
import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.plants.Plant;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.storage.R2StorageService;
import com.dravya.setu.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class IdentificationService {

    private static final Logger log =
            LoggerFactory.getLogger(IdentificationService.class);

    private static final Set<String> VALID_STATUSES = Set.of(
            "KNOWN",
            "UNKNOWN"
    );

    private final IdentificationRepository identificationRepository;
    private final IdentificationImageRepository identificationImageRepository;
    private final PlantRepository plantRepository;
    private final AiClient aiClient;
    private final R2StorageService r2StorageService;
    private final IdentificationPersistenceService persistenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdentificationService(
            IdentificationRepository identificationRepository,
            IdentificationImageRepository identificationImageRepository,
            PlantRepository plantRepository,
            AiClient aiClient,
            R2StorageService r2StorageService,
            IdentificationPersistenceService persistenceService) {

        this.identificationRepository = identificationRepository;
        this.identificationImageRepository = identificationImageRepository;
        this.plantRepository = plantRepository;
        this.aiClient = aiClient;
        this.r2StorageService = r2StorageService;
        this.persistenceService = persistenceService;
    }

    public IdentificationResponse createIdentification(
            IdentificationRequest request,
            User user) {

        return createIdentification(request, user, null);
    }

    /**
     * Creates an identification using a request key generated at the API edge.
     * External storage and AI calls intentionally run before database persistence
     * so a JDBC transaction is not held while waiting on network I/O.
     */
    public IdentificationResponse createIdentification(
            IdentificationRequest request,
            User user,
            String idempotencyKey) {

        // 1. Validate application request
        if (user == null) {
            throw new IllegalArgumentException(
                    "Authenticated user is required");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Identification> existing = identificationRepository
                    .findByUserIdAndRequestId(user.getId(), idempotencyKey);
            if (existing.isPresent()) {
                return toExistingResponse(existing.get());
            }
        }

        log.info(
                "Starting identification for userId={}",
                user.getId()
        );

        if (request == null || request.getImage() == null || request.getImage().isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }

        MultipartFile imageFile = request.getImage();

        String imageUrl = null;

        // Compute requestId BEFORE the try block so it's available in catch block
        // for handling concurrent duplicate requests (DataIntegrityViolationException)
        String requestId = idempotencyKey == null || idempotencyKey.isBlank()
                ? null // will be set to AI response requestId after AI call
                : idempotencyKey;

        try {
            // 2. Upload image to R2 storage
            String folder = "identifications/" + user.getId();
            imageUrl = r2StorageService.uploadImage(imageFile, folder);

            log.info("Image uploaded to R2: {}", imageUrl);

            // 3. Send image reference to AI service
            AiRequest aiRequest = new AiRequest(imageUrl);
            AiResponse aiResponse = aiClient.identify(aiRequest);

            // 3. Validate top-level AI response
            validateAiResponse(aiResponse);

            // Finalize requestId: use idempotencyKey if provided, else AI response requestId
            if (requestId == null) {
                requestId = aiResponse.getRequestId();
            }
            Optional<Identification> existing = identificationRepository
                    .findByUserIdAndRequestId(user.getId(), requestId);
            if (existing.isPresent()) {
                deleteImageQuietly(imageUrl);
                return toExistingResponse(existing.get());
            }

            AiResponse.Identification aiIdentification =
                    aiResponse.getIdentification();

            String status = aiIdentification.getStatus();

            String plantId = aiIdentification.getPlantId();

            Double confidence = aiIdentification.getConfidence();

            log.info(
                "AI response received: requestId={}, plantId={}, status={}, confidence={}, modelVersion={}",
                aiResponse.getRequestId(),
                plantId,
                status,
                confidence,
                aiResponse.getModelVersion()
        );

            // 4. Resolve shared plant_id -> internal plants.id
            Long internalPlantId = null;

            if ("KNOWN".equals(status)) {

            if (plantId == null ||
                    plantId.isBlank()) {

                throw new AiServiceException(
                        "KNOWN identification requires plant_id");
            }

            Plant plant =
                    plantRepository
                            .findByPlantCode(plantId)
                            .orElseThrow(() ->
                                    new AiServiceException(
                                            "Plant not found for plant_id: "
                                                    + plantId
                                    )
                            );

                internalPlantId = plant.getId();
            }

            // 5. Create identification record
            Identification identification = new Identification();

            identification.setUserId(user.getId());

            identification.setPlantId(internalPlantId);

            identification.setStatus(status);

            identification.setConfidence(BigDecimal.valueOf(confidence));

            identification.setModelVersion(aiResponse.getModelVersion());

            identification.setRequestId(requestId);

            // 6. Save image metadata
            IdentificationImage image = new IdentificationImage();
            image.setImageReference(imageUrl);

            image.setPlantPart(request.getPlantPart());

            image.setSequenceNumber(1);

            IdentificationResponse response = new IdentificationResponse(
                    requestId, status, plantId, confidence,
                    aiResponse.getModelVersion(), aiResponse.getAlternatives(),
                    aiResponse.getSimilarSpecies(), aiResponse.getExplanation(),
                    aiResponse.getMultiImage(), aiResponse.getWarnings());
            identification.setResponsePayload(objectMapper.writeValueAsString(response));
            identification = persistenceService.save(identification, image);

            log.info(
                "Identification created: identificationId={}, userId={}, plantId={}",
                identification.getId(),
                user.getId(),
                identification.getPlantId()
        );

            // 7. Construct application-level response
            return response;
        } catch (DataIntegrityViolationException ex) {
            if (requestId != null) {
                Optional<Identification> existing = identificationRepository
                        .findByUserIdAndRequestId(user.getId(), requestId);
                if (existing.isPresent()) {
                    deleteImageQuietly(imageUrl);
                    return toExistingResponse(existing.get());
                }
            }
            deleteImageQuietly(imageUrl);
            throw ex;
        } catch (RuntimeException ex) {
            deleteImageQuietly(imageUrl);
            throw ex;
        }
    }

    private IdentificationResponse toExistingResponse(Identification identification) {
        if (identification.getResponsePayload() != null &&
                !identification.getResponsePayload().isBlank()) {
            try {
                return objectMapper.readValue(
                        identification.getResponsePayload(),
                        IdentificationResponse.class);
            } catch (RuntimeException ex) {
                log.warn("Could not deserialize identification response snapshot: {}",
                        identification.getId(), ex);
            }
        }
        String plantCode = identification.getPlantId() == null ? null
                : plantRepository.findById(identification.getPlantId())
                .map(Plant::getPlantCode)
                .orElse(null);
        return new IdentificationResponse(
                identification.getRequestId(), identification.getStatus(), plantCode,
                identification.getConfidence() == null ? null
                        : identification.getConfidence().doubleValue(),
                identification.getModelVersion(), List.of(), List.of(), null, null,
                List.of());
    }

    private void deleteImageQuietly(String imageUrl) {
        if (imageUrl == null) {
            return;
        }
        try {
            r2StorageService.deleteImage(imageUrl);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Could not remove orphaned R2 image: {}", imageUrl,
                    cleanupFailure);
        }
    }

    /**
     * Validates the required structure of the canonical
     * AI response contract.
     */
    private void validateAiResponse(
            AiResponse aiResponse) {

        if (aiResponse == null) {
            throw new AiServiceException(
                    "AI service returned an empty response");
        }

        // request_id
        if (aiResponse.getRequestId() == null ||
                aiResponse.getRequestId().isBlank()) {

            throw new AiServiceException(
                    "AI service returned an invalid request_id");
        }

        // model_version
        if (aiResponse.getModelVersion() == null ||
                aiResponse.getModelVersion().isBlank()) {

            throw new AiServiceException(
                    "AI service returned an invalid model_version");
        }

        // image_quality
        if (aiResponse.getImageQuality() == null) {
            throw new AiServiceException(
                    "AI service response is missing image_quality");
        }

        if (aiResponse.getImageQuality().getIssues() == null) {
            throw new AiServiceException(
                    "AI service response is missing image_quality.issues");
        }

        // identification
        if (aiResponse.getIdentification() == null) {
            throw new AiServiceException(
                    "AI service response is missing identification");
        }

        AiResponse.Identification identification =
                aiResponse.getIdentification();

        // identification.status
        if (identification.getStatus() == null ||
                !VALID_STATUSES.contains(
                        identification.getStatus())) {

            throw new AiServiceException(
                    "AI service returned an invalid identification status");
        }

        // identification.confidence
        if (identification.getConfidence() == null ||
                identification.getConfidence() < 0 ||
                identification.getConfidence() > 1) {

            throw new AiServiceException(
                    "AI service returned invalid confidence");
        }

        // alternatives
        if (aiResponse.getAlternatives() == null) {
            throw new AiServiceException(
                    "AI service response is missing alternatives");
        }

        // similar_species
        if (aiResponse.getSimilarSpecies() == null) {
            throw new AiServiceException(
                    "AI service response is missing similar_species");
        }

        // explanation
        if (aiResponse.getExplanation() == null) {
            throw new AiServiceException(
                    "AI service response is missing explanation");
        }

        if (!"GRAD_CAM".equals(
                aiResponse.getExplanation().getMethod())) {

            throw new AiServiceException(
                    "AI service returned an invalid explanation method");
        }

        // multi_image
        if (aiResponse.getMultiImage() == null) {
            throw new AiServiceException(
                    "AI service response is missing multi_image");
        }

        if (aiResponse.getMultiImage().getImageCount() == null ||
                aiResponse.getMultiImage().getImageCount() < 1) {

            throw new AiServiceException(
                    "AI service returned an invalid multi_image.image_count");
        }

        // warnings
        if (aiResponse.getWarnings() == null) {
            throw new AiServiceException(
                    "AI service response is missing warnings");
        }
    }

    // Existing ownership method from Phase 7
    public Identification getIdentificationForUser(
            Long identificationId,
            User user) {

        Identification identification =
                identificationRepository.findById(
                                identificationId)
                        .orElseThrow(() ->
                                ClientErrorException.notFound(
                                        "Identification not found"));

        if ("ADMIN".equals(user.getRole())) {
            return identification;
        }

        if (!identification.getUserId()
                .equals(user.getId())) {

            throw new AccessDeniedException(
                    "You do not have access to this identification");
        }

        return identification;
    }
}
