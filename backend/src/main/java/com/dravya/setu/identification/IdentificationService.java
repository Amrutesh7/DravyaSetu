package com.dravya.setu.identification;

import com.dravya.setu.ai.AiClient;
import com.dravya.setu.ai.AiRequest;
import com.dravya.setu.ai.AiResponse;
import com.dravya.setu.ai.AiServiceException;
import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.plants.Plant;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.storage.ImageContent;
import com.dravya.setu.storage.ObjectKeys;
import com.dravya.setu.storage.StorageException;
import com.dravya.setu.storage.StorageService;
import com.dravya.setu.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final IdentificationHeatmapRepository identificationHeatmapRepository;
    private final PlantRepository plantRepository;
    private final AiClient aiClient;
    private final IdentificationPersistenceService persistenceService;
    private final StorageService storageService;
    private final HeatmapExtractor heatmapExtractor;
    private final int maxImages;
    private final String imageTransport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdentificationService(
            IdentificationRepository identificationRepository,
            IdentificationImageRepository identificationImageRepository,
            IdentificationHeatmapRepository identificationHeatmapRepository,
            PlantRepository plantRepository,
            AiClient aiClient,
            IdentificationPersistenceService persistenceService,
            StorageService storageService,
            HeatmapExtractor heatmapExtractor,
            @Value("${app.identification.max-images:5}") int maxImages,
            @Value("${app.ai.image-transport:multipart}") String imageTransport) {

        this.identificationRepository = identificationRepository;
        this.identificationImageRepository = identificationImageRepository;
        this.identificationHeatmapRepository = identificationHeatmapRepository;
        this.plantRepository = plantRepository;
        this.aiClient = aiClient;
        this.persistenceService = persistenceService;
        this.storageService = storageService;
        this.heatmapExtractor = heatmapExtractor;
        this.maxImages = maxImages;
        this.imageTransport = imageTransport;
    }

    public IdentificationResponse createIdentification(
            IdentificationRequest request,
            User user) {

        return createIdentification(request, user, null);
    }

    public IdentificationResponse createIdentification(
            IdentificationRequest request,
            User user,
            String idempotencyKey) {

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

        List<MultipartFile> imageFiles = validateRequest(request);
        if (request.getPlantPart() != null && request.getPlantPart().length() > 50) {
            throw ClientErrorException.badRequest("plantPart must be at most 50 characters");
        }

        log.info(
                "Starting identification for userId={} with {} image(s)",
                user.getId(),
                imageFiles.size()
        );

        String requestId = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString() : idempotencyKey;
        String storageId = UUID.randomUUID().toString();

        List<PreparedImage> prepared = prepareImages(imageFiles);
        boolean useUrls = storageService.isEnabled()
                && "url".equalsIgnoreCase(imageTransport);
        List<String> uploadedKeys = new ArrayList<>();
        boolean persisted = false;

        try {
            if (useUrls) {
                for (PreparedImage image : prepared) {
                    uploadImage(image, user.getId(), storageId);
                    uploadedKeys.add(image.key);
                }
            }

            AiRequest aiRequest = useUrls
                    ? new AiRequest(
                            List.of(),
                            prepared.stream()
                                    .map(image -> storageService.aiUrl(image.key))
                                    .toList(),
                            requestId,
                            request.getPlantPart())
                    : new AiRequest(
                            imageFiles,
                            List.of(),
                            requestId,
                            request.getPlantPart());

            AiResponse aiResponse = aiClient.identify(aiRequest);
            validateAiResponse(aiResponse);

            Optional<Identification> existing = identificationRepository
                    .findByUserIdAndRequestId(user.getId(), requestId);
            if (existing.isPresent()) {
                cleanup(uploadedKeys);
                return toExistingResponse(existing.get());
            }

            if (!useUrls && storageService.isEnabled()) {
                for (PreparedImage image : prepared) {
                    uploadImage(image, user.getId(), storageId);
                    uploadedKeys.add(image.key);
                }
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

            Long internalPlantId = null;
            if ("KNOWN".equals(status)) {
                if (plantId == null || plantId.isBlank()) {
                    throw new AiServiceException(
                            "KNOWN identification requires plant_id");
                }
                Plant plant = plantRepository
                        .findByPlantCode(plantId)
                        .orElseThrow(() -> new AiServiceException(
                                "Plant not found for plant_id: " + plantId));
                internalPlantId = plant.getId();
            }

            Identification identification = new Identification();
            identification.setUserId(user.getId());
            identification.setPlantId(internalPlantId);
            identification.setStatus(status);
            identification.setConfidence(BigDecimal.valueOf(confidence));
            identification.setModelVersion(aiResponse.getModelVersion());
            identification.setRequestId(requestId);

            IdentificationResponse payloadResponse = buildResponse(
                    requestId, aiResponse);
            identification.setResponsePayload(
                    objectMapper.writeValueAsString(payloadResponse));

            List<IdentificationImage> imageEntities = new ArrayList<>();
            for (PreparedImage preparedImage : prepared) {
                IdentificationImage image = new IdentificationImage();
                image.setPlantPart(request.getPlantPart());
                image.setSequenceNumber(preparedImage.sequenceNumber);
                image.setImageReference(preparedImage.key);
                imageEntities.add(image);
            }

            List<IdentificationHeatmapData> heatmaps = archiveHeatmaps(
                    aiResponse,
                    prepared.size(),
                    user.getId(),
                    storageId,
                    uploadedKeys);

            Identification saved = persistenceService.save(
                    identification, imageEntities, heatmaps);
            persisted = true;

            log.info(
                    "Identification created: identificationId={}, userId={}, plantId={}",
                    saved.getId(),
                    user.getId(),
                    saved.getPlantId()
            );

            IdentificationResponse response = buildResponse(requestId, aiResponse);
            response.setImages(loadImageResults(saved.getId()));
            applyPrimaryHeatmapUrl(response, saved.getId());
            return response;
        } catch (DataIntegrityViolationException ex) {
            if (requestId != null) {
                Optional<Identification> existing = identificationRepository
                        .findByUserIdAndRequestId(user.getId(), requestId);
                if (existing.isPresent()) {
                    cleanup(uploadedKeys);
                    return toExistingResponse(existing.get());
                }
            }
            cleanup(uploadedKeys);
            throw ex;
        } catch (RuntimeException ex) {
            if (!persisted) {
                cleanup(uploadedKeys);
            }
            throw ex;
        }
    }

    private List<MultipartFile> validateRequest(IdentificationRequest request) {
        if (request == null || request.getImages() == null
                || request.getImages().isEmpty()) {
            throw new IllegalArgumentException("At least one image is required");
        }
        if (request.getImages().size() > maxImages) {
            throw new IllegalArgumentException(
                    "At most " + maxImages + " images are allowed");
        }
        return request.getImages();
    }

    private List<PreparedImage> prepareImages(List<MultipartFile> images) {
        List<PreparedImage> prepared = new ArrayList<>();
        for (int index = 0; index < images.size(); index++) {
            MultipartFile file = images.get(index);
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Image file is required");
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException exception) {
                throw new IllegalArgumentException("Image could not be read");
            }
            ImageContent.requireSupported(file.getContentType(), bytes);
            String contentType = ImageContent.normalizeContentType(file.getContentType());
            prepared.add(new PreparedImage(
                    index + 1, file, bytes, contentType));
        }
        return prepared;
    }

    private void uploadImage(
            PreparedImage image,
            Long userId,
            String storageId) {
        String key = ObjectKeys.imageKey(
                userId, storageId, image.sequenceNumber, image.contentType);
        storageService.upload(key, image.bytes, image.contentType);
        image.key = key;
    }

    private List<IdentificationHeatmapData> archiveHeatmaps(
            AiResponse aiResponse,
            int imageCount,
            Long userId,
            String storageId,
            List<String> uploadedKeys) {

        if (!storageService.isEnabled()) {
            return List.of();
        }

        List<HeatmapPayload> payloads;
        try {
            payloads = heatmapExtractor.extract(aiResponse, imageCount);
        } catch (RuntimeException exception) {
            log.warn("Heatmap extraction failed: {}", exception.getMessage());
            return List.of();
        }

        List<IdentificationHeatmapData> heatmaps = new ArrayList<>();
        Map<Integer, Integer> sequenceByImage = new HashMap<>();
        for (HeatmapPayload payload : payloads) {
            if (payload.imageIndex() < 0 || payload.imageIndex() >= imageCount) {
                log.warn("Ignoring heatmap for unknown image index {}",
                        payload.imageIndex());
                continue;
            }

            int sequence = sequenceByImage.merge(
                    payload.imageIndex(), 1, Integer::sum);

            if (payload.bytes() != null && payload.bytes().length > 0) {
                String key = ObjectKeys.heatmapKey(
                        userId,
                        storageId,
                        payload.imageIndex() + 1,
                        sequence,
                        payload.contentType());
                storageService.upload(key, payload.bytes(), payload.contentType());
                uploadedKeys.add(key);
                heatmaps.add(new IdentificationHeatmapData(
                        payload.imageIndex(),
                        key,
                        payload.sourceUrl(),
                        payload.contentType(),
                        sequence));
            } else if (payload.sourceUrl() != null
                    && !payload.sourceUrl().isBlank()) {
                heatmaps.add(new IdentificationHeatmapData(
                        payload.imageIndex(),
                        null,
                        payload.sourceUrl(),
                        null,
                        sequence));
            }
        }
        return heatmaps;
    }

    private IdentificationResponse buildResponse(
            String requestId,
            AiResponse aiResponse) {
        AiResponse.Identification aiIdentification =
                aiResponse.getIdentification();
        return new IdentificationResponse(
                requestId,
                aiIdentification.getStatus(),
                aiIdentification.getPlantId(),
                aiIdentification.getConfidence(),
                aiResponse.getModelVersion(),
                aiResponse.getAlternatives(),
                aiResponse.getSimilarSpecies(),
                aiResponse.getExplanation(),
                aiResponse.getMultiImage(),
                aiResponse.getWarnings());
    }

    private IdentificationResponse toExistingResponse(Identification identification) {
        IdentificationResponse response;
        if (identification.getResponsePayload() != null
                && !identification.getResponsePayload().isBlank()) {
            try {
                response = objectMapper.readValue(
                        identification.getResponsePayload(),
                        IdentificationResponse.class);
            } catch (RuntimeException ex) {
                log.warn("Could not deserialize identification response snapshot: {}",
                        identification.getId(), ex);
                response = fallbackResponse(identification);
            }
        } else {
            response = fallbackResponse(identification);
        }

        response.setImages(loadImageResults(identification.getId()));
        applyPrimaryHeatmapUrl(response, identification.getId());
        return response;
    }

    private IdentificationResponse fallbackResponse(Identification identification) {
        String plantCode = identification.getPlantId() == null ? null
                : plantRepository.findById(identification.getPlantId())
                .map(Plant::getPlantCode)
                .orElse(null);
        return new IdentificationResponse(
                identification.getRequestId(),
                identification.getStatus(),
                plantCode,
                identification.getConfidence() == null ? null
                        : identification.getConfidence().doubleValue(),
                identification.getModelVersion(),
                List.of(),
                List.of(),
                null,
                null,
                List.of());
    }

    private List<IdentificationResponse.ImageResult> loadImageResults(
            Long identificationId) {
        List<IdentificationImage> images = identificationImageRepository
                .findByIdentificationIdOrderBySequenceNumberAsc(identificationId);
        Map<Long, List<IdentificationHeatmap>> heatmapsByImage =
                loadHeatmapsByImage(identificationId);

        return images.stream()
                .map(image -> new IdentificationResponse.ImageResult(
                        image.getSequenceNumber(),
                        image.getPlantPart(),
                        displayUrl(image.getImageReference()),
                        heatmapUrls(heatmapsByImage.get(image.getId()))))
                .toList();
    }

    private Map<Long, List<IdentificationHeatmap>> loadHeatmapsByImage(
            Long identificationId) {
        Map<Long, List<IdentificationHeatmap>> byImage = new LinkedHashMap<>();
        for (IdentificationHeatmap heatmap : identificationHeatmapRepository
                .findByIdentificationIdOrderBySequenceNumberAsc(identificationId)) {
            byImage.computeIfAbsent(
                    heatmap.getIdentificationImageId(),
                    ignored -> new ArrayList<>()).add(heatmap);
        }
        return byImage;
    }

    private List<String> heatmapUrls(List<IdentificationHeatmap> heatmaps) {
        if (heatmaps == null || heatmaps.isEmpty()) {
            return List.of();
        }
        return heatmaps.stream()
                .map(this::heatmapUrl)
                .filter(Objects::nonNull)
                .toList();
    }

    private String heatmapUrl(IdentificationHeatmap heatmap) {
        if (heatmap.getStorageKey() != null
                && !heatmap.getStorageKey().isBlank()) {
            return displayUrl(heatmap.getStorageKey());
        }
        if (heatmap.getSourceUrl() != null
                && !heatmap.getSourceUrl().isBlank()) {
            return heatmap.getSourceUrl();
        }
        return null;
    }

    private void applyPrimaryHeatmapUrl(
            IdentificationResponse response,
            Long identificationId) {
        if (response == null || response.getExplanation() == null) {
            return;
        }
        String primary = identificationHeatmapRepository
                .findByIdentificationIdOrderBySequenceNumberAsc(identificationId)
                .stream()
                .map(this::heatmapUrl)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (primary != null) {
            response.getExplanation().setHeatmapUrl(primary);
        }
    }

    private String displayUrl(String key) {
        if (key == null || key.isBlank() || !storageService.isEnabled()) {
            return null;
        }
        try {
            return storageService.displayUrl(key);
        } catch (StorageException exception) {
            log.warn("Could not create display URL for object {}: {}",
                    key, exception.getMessage());
            return null;
        }
    }

    private void cleanup(List<String> uploadedKeys) {
        for (String key : uploadedKeys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException exception) {
                log.warn("Could not clean up B2 object {}: {}",
                        key, exception.getMessage());
            }
        }
    }

    private void validateAiResponse(
            AiResponse aiResponse) {

        if (aiResponse == null) {
            throw new AiServiceException(
                    "AI service returned an empty response");
        }

        if (aiResponse.getRequestId() == null ||
                aiResponse.getRequestId().isBlank()) {

            throw new AiServiceException(
                    "AI service returned an invalid request_id");
        }

        if (aiResponse.getModelVersion() == null ||
                aiResponse.getModelVersion().isBlank()) {

            throw new AiServiceException(
                    "AI service returned an invalid model_version");
        }

        if (aiResponse.getImageQuality() == null) {
            throw new AiServiceException(
                    "AI service response is missing image_quality");
        }

        if (aiResponse.getImageQuality().getIssues() == null) {
            throw new AiServiceException(
                    "AI service response is missing image_quality.issues");
        }

        if (aiResponse.getIdentification() == null) {
            throw new AiServiceException(
                    "AI service response is missing identification");
        }

        AiResponse.Identification identification =
                aiResponse.getIdentification();

        if (identification.getStatus() == null ||
                !VALID_STATUSES.contains(
                        identification.getStatus())) {

            throw new AiServiceException(
                    "AI service returned an invalid identification status");
        }

        if (identification.getConfidence() == null ||
                !Double.isFinite(identification.getConfidence()) ||
                identification.getConfidence() < 0 ||
                identification.getConfidence() > 1) {

            throw new AiServiceException(
                    "AI service returned invalid confidence");
        }

        if (("UNKNOWN".equals(identification.getStatus()) && identification.getPlantId() != null)
                || ("KNOWN".equals(identification.getStatus())
                && (identification.getPlantId() == null || identification.getPlantId().isBlank()))) {
            throw new AiServiceException("AI status and plant_id are inconsistent");
        }
        if (aiResponse.getModelVersion().length() > 100) {
            throw new AiServiceException("AI model_version exceeds the database contract");
        }

        if (aiResponse.getAlternatives() == null) {
            throw new AiServiceException(
                    "AI service response is missing alternatives");
        }

        if (aiResponse.getSimilarSpecies() == null) {
            throw new AiServiceException(
                    "AI service response is missing similar_species");
        }

        if (aiResponse.getExplanation() == null) {
            throw new AiServiceException(
                    "AI service response is missing explanation");
        }

        if (!"GRAD_CAM".equals(
                aiResponse.getExplanation().getMethod())) {

            throw new AiServiceException(
                    "AI service returned an invalid explanation method");
        }

        if (aiResponse.getMultiImage() == null) {
            throw new AiServiceException(
                    "AI service response is missing multi_image");
        }

        if (aiResponse.getMultiImage().getImageCount() == null ||
                aiResponse.getMultiImage().getImageCount() < 1) {

            throw new AiServiceException(
                    "AI service returned an invalid multi_image.image_count");
        }

        if (aiResponse.getWarnings() == null) {
            throw new AiServiceException(
                    "AI service response is missing warnings");
        }
    }

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

            throw ClientErrorException.notFound("Identification not found");
        }

        return identification;
    }

    public IdentificationHistoryResponse getHistoryItem(Long id, User user) {
        return toHistoryResponse(getIdentificationForUser(id, user));
    }

    public Page<IdentificationHistoryResponse> getHistory(
            User user, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw ClientErrorException.badRequest(
                    "page must be non-negative and size must be between 1 and 100");
        }
        Sort.Direction direction = "oldest".equalsIgnoreCase(sort)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return identificationRepository.findByUserId(
                        user.getId(),
                        PageRequest.of(page, size, Sort.by(direction, "createdAt", "id")))
                .map(this::toHistoryResponse);
    }

    private IdentificationHistoryResponse toHistoryResponse(
            Identification identification) {
        String plantCode = identification.getPlantId() == null ? null
                : plantRepository.findById(identification.getPlantId())
                .map(Plant::getPlantCode)
                .orElse(null);

        List<IdentificationHistoryResponse.Image> images = loadImageResults(
                identification.getId()).stream()
                .map(image -> new IdentificationHistoryResponse.Image(
                        image.getPlantPart(),
                        image.getSequenceNumber(),
                        image.getImageUrl(),
                        image.getHeatmapUrls()))
                .toList();

        return new IdentificationHistoryResponse(
                identification.getId(),
                identification.getRequestId(),
                identification.getStatus(),
                plantCode,
                identification.getConfidence() == null ? null
                        : identification.getConfidence().doubleValue(),
                identification.getModelVersion(),
                identification.getCreatedAt(),
                images);
    }

    private static final class PreparedImage {
        private final int sequenceNumber;
        private final MultipartFile file;
        private final byte[] bytes;
        private final String contentType;
        private String key;

        private PreparedImage(
                int sequenceNumber,
                MultipartFile file,
                byte[] bytes,
                String contentType) {
            this.sequenceNumber = sequenceNumber;
            this.file = file;
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }
}
