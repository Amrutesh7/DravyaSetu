package com.dravya.setu.identification;

import com.dravya.setu.ai.AiClient;
import com.dravya.setu.ai.AiRequest;
import com.dravya.setu.ai.AiResponse;
import com.dravya.setu.plants.Plant;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.storage.StorageService;
import com.dravya.setu.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentificationServiceTest {
    @Mock private IdentificationRepository identificationRepository;
    @Mock private IdentificationImageRepository identificationImageRepository;
    @Mock private IdentificationHeatmapRepository identificationHeatmapRepository;
    @Mock private PlantRepository plantRepository;
    @Mock private AiClient aiClient;
    @Mock private IdentificationPersistenceService persistenceService;
    @Mock private StorageService storageService;
    @Mock private HeatmapExtractor heatmapExtractor;
    private IdentificationService service;

    @BeforeEach
    void setUp() {
        when(storageService.isEnabled()).thenReturn(false);
        when(identificationHeatmapRepository
                .findByIdentificationIdOrderBySequenceNumberAsc(any()))
                .thenReturn(List.of());
        service = new IdentificationService(
                identificationRepository,
                identificationImageRepository,
                identificationHeatmapRepository,
                plantRepository,
                aiClient,
                persistenceService,
                storageService,
                heatmapExtractor,
                5,
                "url");
        when(identificationRepository.findByUserIdAndRequestId(any(), any())).thenReturn(Optional.empty());
        when(persistenceService.save(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void forwardsImageDirectlyToAiAndStoresNoCloudReference() {
        User user = org.mockito.Mockito.mock(User.class); when(user.getId()).thenReturn(1L);
        MockMultipartFile image = new MockMultipartFile("image", "aloe.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
        Plant plant = org.mockito.Mockito.mock(Plant.class); when(plant.getId()).thenReturn(2L);
        when(plantRepository.findByPlantCode("PLANT_001")).thenReturn(Optional.of(plant));
        when(aiClient.identify(any())).thenReturn(knownResponse());

        IdentificationRequest request = new IdentificationRequest();
        request.setImage(image); request.setPlantPart("LEAF");
        var response = service.createIdentification(request, user, "request-1");

        ArgumentCaptor<AiRequest> aiRequest = ArgumentCaptor.forClass(AiRequest.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IdentificationImage>> savedImages =
                ArgumentCaptor.forClass(List.class);
        verify(aiClient).identify(aiRequest.capture());
        verify(persistenceService).save(any(), savedImages.capture(), any());
        assertEquals("request-1", aiRequest.getValue().getRequestId());
        assertEquals("LEAF", aiRequest.getValue().getPlantPart());
        assertEquals("PLANT_001", response.getPlantId());
        assertNull(savedImages.getValue().get(0).getImageReference());
    }

    @Test
    void forwardsEveryUploadedImageToAi() {
        User user = org.mockito.Mockito.mock(User.class); when(user.getId()).thenReturn(1L);
        Plant plant = org.mockito.Mockito.mock(Plant.class); when(plant.getId()).thenReturn(2L);
        when(plantRepository.findByPlantCode("PLANT_001")).thenReturn(Optional.of(plant));
        AiResponse aiResponse = knownResponse();
        aiResponse.getMultiImage().setUsed(true);
        aiResponse.getMultiImage().setImageCount(2);
        when(aiClient.identify(any())).thenReturn(aiResponse);

        IdentificationRequest request = new IdentificationRequest();
        request.setImages(List.of(
                jpeg("leaf.jpg"),
                jpeg("flower.jpg")));
        request.setPlantPart("LEAF");

        service.createIdentification(request, user, "request-2");

        ArgumentCaptor<AiRequest> aiRequest = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiClient).identify(aiRequest.capture());
        assertEquals(2, aiRequest.getValue().getImages().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IdentificationImage>> savedImages =
                ArgumentCaptor.forClass(List.class);
        verify(persistenceService).save(any(), savedImages.capture(), any());
        assertEquals(2, savedImages.getValue().size());
        assertEquals(1, savedImages.getValue().get(0).getSequenceNumber());
        assertEquals(2, savedImages.getValue().get(1).getSequenceNumber());
    }

    @Test
    void urlModeSendsPresignedUrlsAndArchivesImages() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.aiUrl(any())).thenReturn("https://bucket.example/image.jpg?sig=abc");
        User user = org.mockito.Mockito.mock(User.class); when(user.getId()).thenReturn(1L);
        Plant plant = org.mockito.Mockito.mock(Plant.class); when(plant.getId()).thenReturn(2L);
        when(plantRepository.findByPlantCode("PLANT_001")).thenReturn(Optional.of(plant));
        when(aiClient.identify(any())).thenReturn(knownResponse());

        IdentificationRequest request = new IdentificationRequest();
        request.setImage(jpeg("leaf.jpg"));
        request.setPlantPart("LEAF");

        service.createIdentification(request, user, "request-3");

        ArgumentCaptor<AiRequest> aiRequest = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiClient).identify(aiRequest.capture());
        assertEquals(List.of("https://bucket.example/image.jpg?sig=abc"),
                aiRequest.getValue().getImageUrls());
        verify(storageService).upload(any(), any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IdentificationImage>> savedImages =
                ArgumentCaptor.forClass(List.class);
        verify(persistenceService).save(any(), savedImages.capture(), any());
        assertNotNull(savedImages.getValue().get(0).getImageReference());
    }

    @Test
    void archivesHeatmapsAndAssignsPerImageSequenceNumbers() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.aiUrl(any())).thenReturn("https://bucket.example/image.jpg?sig=abc");
        User user = org.mockito.Mockito.mock(User.class); when(user.getId()).thenReturn(1L);
        Plant plant = org.mockito.Mockito.mock(Plant.class); when(plant.getId()).thenReturn(2L);
        when(plantRepository.findByPlantCode("PLANT_001")).thenReturn(Optional.of(plant));
        when(aiClient.identify(any())).thenReturn(knownResponse());

        byte[] heatmapBytes = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1
        };
        when(heatmapExtractor.extract(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(
                        new HeatmapPayload(0, heatmapBytes, "image/png", null),
                        new HeatmapPayload(0, heatmapBytes, "image/png", null)));

        IdentificationRequest request = new IdentificationRequest();
        request.setImage(jpeg("leaf.jpg"));
        request.setPlantPart("LEAF");

        service.createIdentification(request, user, "request-4");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IdentificationHeatmapData>> savedHeatmaps =
                ArgumentCaptor.forClass(List.class);
        verify(persistenceService).save(any(), any(), savedHeatmaps.capture());

        assertEquals(2, savedHeatmaps.getValue().size());
        assertEquals(0, savedHeatmaps.getValue().get(0).imageIndex());
        assertEquals(1, savedHeatmaps.getValue().get(0).sequenceNumber());
        assertEquals(0, savedHeatmaps.getValue().get(1).imageIndex());
        assertEquals(2, savedHeatmaps.getValue().get(1).sequenceNumber());
        assertNotNull(savedHeatmaps.getValue().get(0).storageKey());
        assertEquals("image/png", savedHeatmaps.getValue().get(0).contentType());

        // One image upload plus two heatmap uploads.
        verify(storageService, org.mockito.Mockito.times(3))
                .upload(any(), any(), any());
    }

    @Test
    void rejectsUnsupportedImageBeforeCallingAi() {
        User user = org.mockito.Mockito.mock(User.class); when(user.getId()).thenReturn(1L);
        IdentificationRequest request = new IdentificationRequest();
        request.setImage(new MockMultipartFile("image", "bad.pdf", "application/pdf", new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> service.createIdentification(request, user));
    }

    private MockMultipartFile jpeg(String filename) {
        return new MockMultipartFile("image", filename, "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
    }

    @Test
    void rejectsContradictoryAiResultsAndNonFiniteConfidenceBeforePersistence() {
        User user = org.mockito.Mockito.mock(User.class); when(user.getId()).thenReturn(1L);
        IdentificationRequest request = new IdentificationRequest(); request.setImage(jpeg("leaf.jpg"));
        AiResponse contradictory = knownResponse(); contradictory.getIdentification().setStatus("UNKNOWN");
        when(aiClient.identify(any())).thenReturn(contradictory);
        assertThrows(com.dravya.setu.ai.AiServiceException.class, () -> service.createIdentification(request,user));
        AiResponse invalid = knownResponse(); invalid.getIdentification().setConfidence(Double.NaN);
        when(aiClient.identify(any())).thenReturn(invalid);
        assertThrows(com.dravya.setu.ai.AiServiceException.class, () -> service.createIdentification(request,user));
        org.mockito.Mockito.verifyNoInteractions(persistenceService);
    }

    private AiResponse knownResponse() {
        AiResponse response = new AiResponse();
        response.setRequestId("ai-request"); response.setModelVersion("mock-plant-v1.0");
        AiResponse.ImageQuality quality = new AiResponse.ImageQuality(); quality.setIssues(List.of()); quality.setAcceptable(true);
        response.setImageQuality(quality);
        AiResponse.Identification identification = new AiResponse.Identification();
        identification.setStatus("KNOWN"); identification.setPlantId("PLANT_001"); identification.setConfidence(.9);
        response.setIdentification(identification);
        response.setAlternatives(List.of()); response.setSimilarSpecies(List.of());
        AiResponse.Explanation explanation = new AiResponse.Explanation(); explanation.setMethod("GRAD_CAM"); response.setExplanation(explanation);
        AiResponse.MultiImage multiImage = new AiResponse.MultiImage(); multiImage.setImageCount(1); multiImage.setUsed(false); response.setMultiImage(multiImage);
        response.setWarnings(List.of()); return response;
    }
}
