package com.dravya.setu.ai;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiResponse {

    private String requestId;
    private String modelVersion;
    private ImageQuality imageQuality;
    private Identification identification;
    private List<Alternative> alternatives;
    private List<SimilarSpecies> similarSpecies;
    private Explanation explanation;
    private MultiImage multiImage;
    private List<String> warnings;

    public AiResponse() {
    }

    public String getRequestId() {
        return requestId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public ImageQuality getImageQuality() {
        return imageQuality;
    }

    public Identification getIdentification() {
        return identification;
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

    public List<String> getWarnings() {
        return warnings;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public void setImageQuality(ImageQuality imageQuality) {
        this.imageQuality = imageQuality;
    }

    public void setIdentification(Identification identification) {
        this.identification = identification;
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

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ImageQuality {

        private boolean acceptable;
        private List<String> issues;

        public ImageQuality() {
        }

        public boolean isAcceptable() {
            return acceptable;
        }

        public List<String> getIssues() {
            return issues;
        }

        public void setAcceptable(boolean acceptable) {
            this.acceptable = acceptable;
        }

        public void setIssues(List<String> issues) {
            this.issues = issues;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Identification {

        private String status;
        private String plantId;
        private Double confidence;

        public Identification() {
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

        public void setStatus(String status) {
            this.status = status;
        }

        public void setPlantId(String plantId) {
            this.plantId = plantId;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Alternative {

        private String plantId;
        private String commonName;
        private String scientificName;
        private Double confidence;

        public Alternative() {
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