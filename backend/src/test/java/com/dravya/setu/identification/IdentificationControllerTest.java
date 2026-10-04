package com.dravya.setu.identification;

import com.dravya.setu.common.GlobalExceptionHandler;
import com.dravya.setu.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class IdentificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IdentificationService identificationService;

    private User testUser;

    @BeforeEach
    void setUp() {

        IdentificationController identificationController =
                new IdentificationController(
                        identificationService
                );

        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setRole("USER");

        HandlerMethodArgumentResolver userResolver =
                new HandlerMethodArgumentResolver() {

                    @Override
                    public boolean supportsParameter(
                            MethodParameter parameter) {

                        return parameter.hasParameterAnnotation(
                                AuthenticationPrincipal.class
                        );
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory) {

                        return testUser;
                    }
                };

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        identificationController
                )
                .setCustomArgumentResolvers(
                        userResolver
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createIdentification_shouldReturn201()
            throws Exception {

        IdentificationResponse response =
                createSuccessResponse();

        when(
                        identificationService.createIdentification(
                                any(IdentificationRequest.class),
                        any(User.class),
                        any()
                )
        ).thenReturn(response);

        MockMultipartFile imageFile = new MockMultipartFile(
                "image",
                "plant.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );

        mockMvc.perform(
                        MockMvcRequestBuilders.multipart("/identifications")
                                .file(imageFile)
                                .param("plantPart", "LEAF")
                )
                .andExpect(
                        status().isCreated()
                );
    }

    @Test
    void createIdentification_withMissingImage_shouldReturn400()
            throws Exception {

        mockMvc.perform(
                        MockMvcRequestBuilders.multipart("/identifications")
                                .param("plantPart", "LEAF")
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    @Test
    void createIdentification_withEmptyFile_shouldReturn400()
            throws Exception {

        when(identificationService.createIdentification(
                any(IdentificationRequest.class), any(User.class), any()))
                .thenThrow(new IllegalArgumentException("Image file is required"));

        MockMultipartFile emptyFile = new MockMultipartFile(
                "image",
                "empty.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[0]
        );

        mockMvc.perform(
                        MockMvcRequestBuilders.multipart("/identifications")
                                .file(emptyFile)
                                .param("plantPart", "LEAF")
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    private IdentificationResponse createSuccessResponse() {

        IdentificationResponse response =
                new IdentificationResponse();

        response.setRequestId(
                "test-request-001"
        );

        response.setStatus(
                "KNOWN"
        );

        response.setPlantId(
                "PLANT_001"
        );

        response.setConfidence(
                0.92
        );

        response.setModelVersion(
                "mock-model-v2"
        );

        response.setWarnings(
                java.util.List.of()
        );

        response.setAlternatives(
                java.util.List.of()
        );

        response.setSimilarSpecies(
                java.util.List.of()
        );

        IdentificationResponse.Explanation explanation =
                new IdentificationResponse.Explanation();

        explanation.setMethod("GRAD_CAM");
        explanation.setHeatmapUrl(null);

        response.setExplanation(
                explanation
        );

        IdentificationResponse.MultiImage multiImage =
                new IdentificationResponse.MultiImage();

        multiImage.setUsed(false);
        multiImage.setImageCount(1);

        response.setMultiImage(
                multiImage
        );

        return response;
    }
}
