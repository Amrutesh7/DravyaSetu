package com.dravya.setu.identification;

import com.dravya.setu.ai.AiResponse;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class IdentificationResponse {

    private String requestId;
    private String status;
    private String plantId;
    private Double confidence;
    private String modelVersion;
    private List<String> warnings;
    private List<Alternative> alternatives;
    private List<SimilarSpecies> similarSpecies;
    private Explanation explanation;
    private MultiImage multiImage;

    public IdentificationResponse() {
    }

    public IdentificationResponse(
            String requestId,
            String status,
            String plantId,
            Double confidence,
            String modelVersion,
            List<AiResponse.Alternative> alternatives,
            List<AiResponse.SimilarSpecies> similarSpecies,
            AiResponse.Explanation explanation,
            AiResponse.MultiImage multiImage,
            List<String> warnings) {

        this.requestId = requestId;
        this.status = status;
        this.plantId = plantId;
        this.confidence = confidence;
        this.modelVersion = modelVersion;
        this.alternatives =
                alternatives == null
                        ? List.of()
                        : alternatives.stream()
                        .map(Alternative::fromAi)
                        .toList();

        this.similarSpecies =
                similarSpecies == null
                        ? List.of()
                        : similarSpecies.stream()
                        .map(SimilarSpecies::fromAi)
                        .toList();

        this.explanation =
                explanation == null
                        ? null
                        : Explanation.fromAi(explanation);

        this.multiImage =
                multiImage == null
                        ? null
                        : MultiImage.fromAi(multiImage);

        this.warnings =
                warnings == null
                        ? List.of()
                        : List.copyOf(warnings);
    }

    public String getRequestId() {
        return requestId;
    }

    public String getStatus() {
        return status;
    }

    public String getPlantId() {
        return plantId;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<Alternative> getAlternatives() {
        return alternatives;
    }

    public List<SimilarSpecies> getSimilarSpecies() {
        return similarSpecies;
    }

    public Explanation getExplanation() {
        return explanation;
    }

    public MultiImage getMultiImage() {
        return multiImage;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPlantId(String plantId) {
        this.plantId = plantId;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public void setAlternatives(List<Alternative> alternatives) {
        this.alternatives = alternatives;
    }

    public void setSimilarSpecies(List<SimilarSpecies> similarSpecies) {
        this.similarSpecies = similarSpecies;
    }

    public void setExplanation(Explanation explanation) {
        this.explanation = explanation;
    }

    public void setMultiImage(MultiImage multiImage) {
        this.multiImage = multiImage;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Alternative {

        private String plantId;
        private String commonName;
        private String scientificName;
        private Double confidence;

        public Alternative() {
        }

        static Alternative fromAi(
                AiResponse.Alternative source) {

            Alternative target = new Alternative();

            target.plantId = source.getPlantId();
            target.commonName = source.getCommonName();
            target.scientificName = source.getScientificName();
            target.confidence = source.getConfidence();

            return target;
        }

        public String getPlantId() {
            return plantId;
        }

        public String getCommonName() {
            return commonName;
        }

        public String getScientificName() {
            return scientificName;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setPlantId(String plantId) {
            this.plantId = plantId;
        }

        public void setCommonName(String commonName) {
            this.commonName = commonName;
        }

        public void setScientificName(String scientificName) {
            this.scientificName = scientificName;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SimilarSpecies {

        private String plantId;
        private String name;
        private Double similarity;

        public SimilarSpecies() {
        }

        static SimilarSpecies fromAi(
                AiResponse.SimilarSpecies source) {

            SimilarSpecies target = new SimilarSpecies();

            target.plantId = source.getPlantId();
            target.name = source.getName();
            target.similarity = source.getSimilarity();

            return target;
        }

        public String getPlantId() {
            return plantId;
        }

        public String getName() {
            return name;
        }

        public Double getSimilarity() {
            return similarity;
        }

        public void setPlantId(String plantId) {
            this.plantId = plantId;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSimilarity(Double similarity) {
            this.similarity = similarity;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Explanation {

        private String method;
        private String heatmapUrl;

        public Explanation() {
        }

        static Explanation fromAi(
                AiResponse.Explanation source) {

            Explanation target = new Explanation();

            target.method = source.getMethod();
            target.heatmapUrl = source.getHeatmapUrl();

            return target;
        }

        public String getMethod() {
            return method;
        }

        public String getHeatmapUrl() {
            return heatmapUrl;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public void setHeatmapUrl(String heatmapUrl) {
            this.heatmapUrl = heatmapUrl;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MultiImage {

        private boolean used;
        private Integer imageCount;

        public MultiImage() {
        }

        static MultiImage fromAi(
                AiResponse.MultiImage source) {

            MultiImage target = new MultiImage();

            target.used = source.isUsed();
            target.imageCount = source.getImageCount();

            return target;
        }

        public boolean isUsed() {
            return used;
        }

        public Integer getImageCount() {
            return imageCount;
        }

        public void setUsed(boolean used) {
            this.used = used;
        }

        public void setImageCount(Integer imageCount) {
            this.imageCount = imageCount;
        }
    }
}
