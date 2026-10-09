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
        return new DocumentGenerationRegistrationResult(
                batch.getId(),
                batch.getTopicRegistrationState(),
                findClassificationJobId(jobs),
                findGenerationJobIds(jobs)
        );
    }

    private static Long findClassificationJobId(List<DocumentGenerationJob> jobs) {
        Long classificationJobId = null;
        for (DocumentGenerationJob job : jobs) {
            if (job.getStage() != DocumentGenerationJobStage.CLASSIFICATION) {
                continue;
            }
            classificationJobId = job.getId();
        }
        return classificationJobId;
    }

    private static List<Long> findGenerationJobIds(List<DocumentGenerationJob> jobs) {
        List<Long> generationJobIds = new ArrayList<>();
        for (DocumentGenerationJob job : jobs) {
            if (job.getStage() == DocumentGenerationJobStage.CLASSIFICATION) {
                continue;
            }
            Long jobId = job.getId();
            generationJobIds.add(jobId);
        }
        return generationJobIds;
    }
}
