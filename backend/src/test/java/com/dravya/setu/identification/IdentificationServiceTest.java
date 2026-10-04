package com.dravya.setu.identification;

import com.dravya.setu.ai.AiClient;
import com.dravya.setu.ai.AiRequest;
import com.dravya.setu.ai.AiResponse;
import com.dravya.setu.ai.AiServiceException;
import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.common.IllegalServiceException;
import com.dravya.setu.plants.Plant;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.storage.R2StorageService;
import com.dravya.setu.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentificationServiceTest {

    @Mock
    private IdentificationRepository identificationRepository;

    @Mock
    private IdentificationImageRepository identificationImageRepository;

    @Mock
    private PlantRepository plantRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private R2StorageService r2StorageService;

    @Mock
    private IdentificationPersistenceService persistenceService;

    private IdentificationService identificationService;

    @BeforeEach
    void setUp() {
        identificationService = new IdentificationService(
                identificationRepository,
                identificationImageRepository,
                plantRepository,
                aiClient,
                r2StorageService,
                persistenceService
        );

        when(persistenceService.save(
                any(Identification.class), any(IdentificationImage.class)))
                .thenAnswer(invocation -> {
                    Identification identification = invocation.getArgument(0);
                    IdentificationImage image = invocation.getArgument(1);
                    image.setIdentificationId(identification.getId());
                    return identification;
                });
    }

    @Test
    void createIdentification_shouldCreateSuccessfully() throws IOException {

        User user = createUser(10L, "test@example.com", "USER");

        MultipartFile imageFile = mock(MultipartFile.class);
        when(imageFile.isEmpty()).thenReturn(false);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("plant.jpg");
        doReturn(new byte[]{1, 2, 3}).when(imageFile).getBytes();

        IdentificationRequest request = createRequest(imageFile, "LEAF");

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        AiResponse aiResponse =
                createValidKnownResponse(
                        "test-request-001",
                        "PLANT_001",
                        0.92
                );

        Plant plant =
                createPlant(
                        25L,
                        "PLANT_001",
                        "Neem"
                );

        when(aiClient.identify(any(AiRequest.class)))
                .thenReturn(aiResponse);

        when(plantRepository.findByPlantCode("PLANT_001"))
                .thenReturn(Optional.of(plant));

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        IdentificationResponse response =
                identificationService.createIdentification(
                        request,
                        user
                );

        assertEquals(
                "test-request-001",
                response.getRequestId()
        );

        assertEquals(
                "KNOWN",
                response.getStatus()
        );

        assertEquals(
                "PLANT_001",
                response.getPlantId()
        );

        assertEquals(
                0.92,
                response.getConfidence()
        );

        assertEquals(
                "mock-model-v2",
                response.getModelVersion()
        );

        verify(persistenceService)
                .save(any(Identification.class), any(IdentificationImage.class));
    }

    @Test
    void createIdentification_shouldRejectMissingImageUrl() throws IOException {

        User user = createUser(
                10L,
                "test@example.com",
                "USER"
        );

        MultipartFile imageFile = mock(MultipartFile.class);
        when(imageFile.isEmpty()).thenReturn(true);

        IdentificationRequest request = createRequest(imageFile, "LEAF");

        assertThrows(
                IllegalArgumentException.class,
                () -> identificationService
                        .createIdentification(
                                request,
                                user
                        )
        );

        verify(aiClient, never())
                .identify(any(AiRequest.class));
    }

    @Test
    void createIdentification_shouldRejectNullAiResponse() throws IOException {

        User user = createUser(
                10L,
                "test@example.com",
                "USER"
        );

        MultipartFile imageFile = mock(MultipartFile.class);
        when(imageFile.isEmpty()).thenReturn(false);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("plant.jpg");
        doReturn(new byte[]{1, 2, 3}).when(imageFile).getBytes();

        IdentificationRequest request = createRequest(imageFile, "LEAF");

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        when(aiClient.identify(any(AiRequest.class)))
                .thenReturn(null);

        assertThrows(
                AiServiceException.class,
                () -> identificationService
                        .createIdentification(
                                request,
                                user
                        )
        );
    }

    @Test
    void createIdentification_shouldRejectInvalidAiStatus() throws IOException {

        User user = createUser(
                10L,
                "test@example.com",
                "USER"
        );

        MultipartFile imageFile = mock(MultipartFile.class);
        when(imageFile.isEmpty()).thenReturn(false);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("plant.jpg");
        doReturn(new byte[]{1, 2, 3}).when(imageFile).getBytes();

        IdentificationRequest request = createRequest(imageFile, "LEAF");

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        AiResponse aiResponse =
                createValidKnownResponse(
                        "test-request-002",
                        "PLANT_001",
                        0.92
                );

        aiResponse.getIdentification()
                .setStatus("INVALID_STATUS");

        when(aiClient.identify(any(AiRequest.class)))
                .thenReturn(aiResponse);

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        assertThrows(
                AiServiceException.class,
                () -> identificationService
                        .createIdentification(
                                request,
                                user
                        )
        );
    }

    @Test
    void createIdentification_shouldRejectInvalidConfidence() throws IOException {

        User user = createUser(
                10L,
                "test@example.com",
                "USER"
        );

        MultipartFile imageFile = mock(MultipartFile.class);
        when(imageFile.isEmpty()).thenReturn(false);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("plant.jpg");
        doReturn(new byte[]{1, 2, 3}).when(imageFile).getBytes();

        IdentificationRequest request = createRequest(imageFile, "LEAF");

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        AiResponse aiResponse =
                createValidKnownResponse(
                        "test-request-003",
                        "PLANT_001",
                        1.50
                );

        when(aiClient.identify(any(AiRequest.class)))
                .thenReturn(aiResponse);

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        assertThrows(
                AiServiceException.class,
                () -> identificationService
                        .createIdentification(
                                request,
                                user
                        )
        );
    }

    @Test
    void createIdentification_shouldRejectKnownWithoutPlantId() throws IOException {

        User user = createUser(
                10L,
                "test@example.com",
                "USER"
        );

        MultipartFile imageFile = mock(MultipartFile.class);
        when(imageFile.isEmpty()).thenReturn(false);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("plant.jpg");
        doReturn(new byte[]{1, 2, 3}).when(imageFile).getBytes();

        IdentificationRequest request = createRequest(imageFile, "LEAF");

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        AiResponse aiResponse =
                createValidKnownResponse(
                        "test-request-004",
                        null,
                        0.92
                );

        when(aiClient.identify(any(AiRequest.class)))
                .thenReturn(aiResponse);

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        assertThrows(
                AiServiceException.class,
                () -> identificationService
                        .createIdentification(
                                request,
                                user
                        )
        );

        verify(plantRepository, never())
                .findByPlantCode(any(String.class));
    }

    @Test
    void createIdentification_shouldRejectUnknownPlantId() throws IOException {

        User user = createUser(
                10L,
                "test@example.com",
                "USER"
        );

        MultipartFile imageFile = mock(MultipartFile.class);
        when(imageFile.isEmpty()).thenReturn(false);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("plant.jpg");
        doReturn(new byte[]{1, 2, 3}).when(imageFile).getBytes();

        IdentificationRequest request = createRequest(imageFile, "LEAF");

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        AiResponse aiResponse =
                createValidKnownResponse(
                        "test-request-005",
                        "PLANT_999",
                        0.92
                );

        when(aiClient.identify(any(AiRequest.class)))
                .thenReturn(aiResponse);

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        when(plantRepository.findByPlantCode("PLANT_999"))
                .thenReturn(Optional.empty());

        assertThrows(
                AiServiceException.class,
                () -> identificationService
                        .createIdentification(
                                request,
                                user
                        )
        );
    }

    @Test
    void createIdentification_unknownPlant_shouldSaveWithNullPlantId() throws IOException {

        User user = createUser(
                10L,
                "test@example.com",
                "USER"
        );

        MultipartFile imageFile = mock(MultipartFile.class);
        when(imageFile.isEmpty()).thenReturn(false);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("plant.jpg");
        doReturn(new byte[]{1, 2, 3}).when(imageFile).getBytes();

        IdentificationRequest request = createRequest(imageFile, "LEAF");

        when(r2StorageService.uploadImage(any(MultipartFile.class), anyString()))
                .thenReturn("https://r2.example.com/identifications/10/abc123.jpg");

        AiResponse aiResponse =
                createValidUnknownResponse();

        when(aiClient.identify(any(AiRequest.class)))
                .thenReturn(aiResponse);

        IdentificationResponse response =
                identificationService.createIdentification(
                        request,
                        user
                );

        assertEquals(
                "UNKNOWN",
                response.getStatus()
        );

        assertNull(
                response.getPlantId()
        );

        verify(plantRepository, never())
                .findByPlantCode(any(String.class));

        verify(persistenceService)
                .save(any(Identification.class), any(IdentificationImage.class));
    }

    @Test
    void getIdentificationForUser_shouldAllowOwner() {

        User owner = createUser(
                10L,
                "owner@example.com",
                "USER"
        );

        Identification identification =
                new Identification();

        identification.setUserId(10L);
        identification.setPlantId(25L);
        identification.setStatus("KNOWN");
        identification.setConfidence(
                BigDecimal.valueOf(0.92)
        );
        identification.setModelVersion(
                "mock-model-v2"
        );

        when(identificationRepository.findById(100L))
                .thenReturn(Optional.of(identification));

        Identification result =
                identificationService
                        .getIdentificationForUser(
                                100L,
                                owner
                        );

        assertEquals(
                identification,
                result
        );
    }

    @Test
    void getIdentificationForUser_shouldRejectAnotherUser() {

        User anotherUser = createUser(
                20L,
                "another@example.com",
                "USER"
        );

        Identification identification =
                new Identification();

        identification.setUserId(10L);
        identification.setPlantId(25L);
        identification.setStatus("KNOWN");
        identification.setConfidence(
                BigDecimal.valueOf(0.92)
        );
        identification.setModelVersion(
                "mock-model-v2"
        );

        when(identificationRepository.findById(100L))
                .thenReturn(Optional.of(identification));

        assertThrows(
                AccessDeniedException.class,
                () -> identificationService
                        .getIdentificationForUser(
                                100L,
                                anotherUser
                        )
        );
    }

    @Test
    void getIdentificationForUser_shouldAllowAdmin() {

        User admin = createUser(
                99L,
                "admin@example.com",
                "ADMIN"
        );

        Identification identification =
                new Identification();

        identification.setUserId(10L);
        identification.setPlantId(25L);
        identification.setStatus("KNOWN");
        identification.setConfidence(
                BigDecimal.valueOf(0.92)
        );
        identification.setModelVersion(
                "mock-model-v2"
        );

        when(identificationRepository.findById(100L))
                .thenReturn(Optional.of(identification));

        Identification result =
                identificationService
                        .getIdentificationForUser(
                                100L,
                                admin
                        );

        assertEquals(
                identification,
                result
        );
    }

    private AiResponse createValidKnownResponse(
            String requestId,
            String plantId,
            double confidence) {

        AiResponse response = new AiResponse();

        response.setRequestId(requestId);
        response.setModelVersion("mock-model-v2");

        AiResponse.ImageQuality imageQuality =
                new AiResponse.ImageQuality();

        imageQuality.setAcceptable(true);
        imageQuality.setIssues(List.of());

        response.setImageQuality(imageQuality);

        AiResponse.Identification identification =
                new AiResponse.Identification();

        identification.setStatus("KNOWN");
        identification.setPlantId(plantId);
        identification.setConfidence(confidence);

        response.setIdentification(identification);

        response.setAlternatives(List.of());
        response.setSimilarSpecies(List.of());

        AiResponse.Explanation explanation =
                new AiResponse.Explanation();

        explanation.setMethod("GRAD_CAM");
        explanation.setHeatmapUrl(null);

        response.setExplanation(explanation);

        AiResponse.MultiImage multiImage =
                new AiResponse.MultiImage();

        multiImage.setUsed(false);
        multiImage.setImageCount(1);

        response.setMultiImage(multiImage);

        response.setWarnings(List.of());

        return response;
    }

    private AiResponse createValidUnknownResponse() {

        AiResponse response =
                createValidKnownResponse(
                        "test-request-unknown",
                        null,
                        0.41
        );

        response.getIdentification()
                .setStatus("UNKNOWN");

        return response;
    }

    private IdentificationRequest createRequest(
            MultipartFile imageFile,
            String plantPart) {

        IdentificationRequest request =
                new IdentificationRequest();

        request.setImage(imageFile);
        request.setPlantPart(plantPart);

        return request;
    }

    private Plant createPlant(
            Long id,
            String plantCode,
            String commonName) {

        Plant plant = new Plant();

        setPrivateId(
                plant,
                id
        );

        plant.setPlantCode(plantCode);
        plant.setCommonName(commonName);

        return plant;
    }

    private User createUser(
            Long id,
            String email,
            String role) {

        User user = new User();

        setPrivateId(
                user,
                id
        );

        user.setEmail(email);
        user.setRole(role);

        return user;
    }

    private void setPrivateId(
            Object object,
            Long id) {

        try {
            var field =
                    object.getClass()
                            .getDeclaredField("id");

            field.setAccessible(true);
            field.set(object, id);

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Could not set test ID",
                    e
            );
        }
    }
}
