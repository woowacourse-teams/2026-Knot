package com.knot.backend.document.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class DocumentGenerationInput {

    private final String transcriptContent;
    private final DocumentTopic topic;

    private DocumentGenerationInput(
            String transcriptContent,
            DocumentTopic topic
    ) {
        this.transcriptContent = transcriptContent;
        this.topic = topic;
    }

    public static DocumentGenerationInput of(
            String transcriptContent,
            String topic
    ) {
        try {
            return from(
                    transcriptContent,
                    DocumentTopic.of(topic)
            );
        } catch (DocumentException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT);
        }
    }

    public static DocumentGenerationInput from(
            String transcriptContent,
            DocumentTopic topic
    ) {
        if (DocumentText.isBlank(transcriptContent)) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT);
        }
        if (topic == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT);
        }
        return new DocumentGenerationInput(
                transcriptContent,
                topic
        );
    }
}
