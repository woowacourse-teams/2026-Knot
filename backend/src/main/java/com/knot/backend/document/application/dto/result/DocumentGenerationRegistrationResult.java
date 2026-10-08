package com.knot.backend.document.application.dto.result;

import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobStage;
import com.knot.backend.document.domain.DocumentTopicRegistrationState;
import java.util.ArrayList;
import java.util.List;

public record DocumentGenerationRegistrationResult(
        long batchId,
        DocumentTopicRegistrationState registrationState,
        Long classificationJobId,
        List<Long> generationJobIds
) {

    public DocumentGenerationRegistrationResult {
        generationJobIds = List.copyOf(generationJobIds);
    }

    public static DocumentGenerationRegistrationResult from(
            DocumentGenerationBatch batch,
            List<DocumentGenerationJob> jobs
    ) {
        Long classificationJobId = null;
        List<Long> generationJobIds = new ArrayList<>();
        for (DocumentGenerationJob job : jobs) {
            if (job.getStage() == DocumentGenerationJobStage.CLASSIFICATION) {
                classificationJobId = job.getId();
            } else {
                generationJobIds.add(job.getId());
            }
        }
        return new DocumentGenerationRegistrationResult(
                batch.getId(),
                batch.getTopicRegistrationState(),
                classificationJobId,
                generationJobIds
        );
    }
}
