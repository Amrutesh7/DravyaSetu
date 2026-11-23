package com.dravya.setu.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * One grounded knowledge source returned by Person 3's knowledge service.
 *
 * <p>Shape: {@code {source_id, title, reference}}. Unknown fields are ignored so that the
 * service can add metadata without breaking the backend.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class KnowledgeSource {

    private String sourceId;
    private String title;
    private String reference;

    public KnowledgeSource() {
    }

    public KnowledgeSource(String sourceId, String title, String reference) {
        this.sourceId = sourceId;
        this.title = title;
        this.reference = reference;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getReference() {
        return reference;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
