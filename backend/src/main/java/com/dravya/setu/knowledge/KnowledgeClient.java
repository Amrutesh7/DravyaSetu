package com.dravya.setu.knowledge;

/**
 * Internal contract with Person 3's knowledge service.
 *
 * <p>The deployed service exposes a single text endpoint ({@code POST /knowledge/chat});
 * there is no speech-to-text route, so voice input is not part of this boundary.
 * Spoken <em>output</em> is requested per chat via {@code generate_audio}.</p>
 */
public interface KnowledgeClient {

    KnowledgeResponse ask(KnowledgeRequest request);
}
