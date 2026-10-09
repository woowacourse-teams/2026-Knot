package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationExecution;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationFailureCause;
import com.knot.backend.document.domain.DocumentGenerationJobStage;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.ErrorCode;
import com.knot.backend.global.exception.LlmException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knot.llm", name = "enabled", havingValue = "true")
public class DocumentGenerationWorker {

    private final DocumentGenerationClaimService claims;
    private final DocumentTopicClassificationService classifier;
    private final DocumentGenerationService generator;
    private final DocumentClassificationResultService classificationResults;
    private final DocumentGenerationResultService generationResults;
    private final DocumentGenerationFailureService failures;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void execute(
            long workspaceId,
            long jobId
    ) {
        Optional<DocumentGenerationExecution> claimed = claims.claim(
                workspaceId,
                jobId
        );
        claimed.ifPresent(this::executeClaimed);
    }

    private void executeClaimed(DocumentGenerationExecution execution) {
        try {
            executeStage(execution);
        } catch (LlmException exception) {
            fail(
                    execution,
                    llmCause(exception.getErrorCode())
            );
        } catch (DocumentException exception) {
            fail(
                    execution,
                    documentCause(exception)
            );
        } catch (DataAccessException exception) {
            fail(
                    execution,
                    DocumentGenerationFailureCause.STORAGE
            );
        } catch (RuntimeException exception) {
            fail(
                    execution,
                    DocumentGenerationFailureCause.INTERNAL
            );
        }
    }

    private void executeStage(DocumentGenerationExecution execution) {
        if (execution.stage() == DocumentGenerationJobStage.CLASSIFICATION) {
            classificationResults.completeClassification(
                    execution.workspaceId(),
                    execution.jobId(),
                    execution.attemptCount(),
                    classifier.classify(execution.transcriptContent())
            );
            return;
        }
        generationResults.completeGeneration(
                execution.workspaceId(),
                execution.jobId(),
                execution.attemptCount(),
                generator.generate(
                        execution.transcriptContent(),
                        execution.topic()
                )
        );
    }

    private DocumentGenerationFailureCause llmCause(ErrorCode errorCode) {
        if (errorCode == LlmErrorCode.LLM_TIMEOUT || errorCode == LlmErrorCode.LLM_CALL_INTERRUPTED) {
            return DocumentGenerationFailureCause.TIMEOUT;
        }
        if (errorCode == LlmErrorCode.LLM_RATE_LIMITED) {
            return DocumentGenerationFailureCause.RATE_LIMITED;
        }
        if (errorCode == LlmErrorCode.LLM_UNAVAILABLE) {
            return DocumentGenerationFailureCause.UNAVAILABLE;
        }
        if (errorCode == LlmErrorCode.LLM_AUTHENTICATION_FAILED
                || errorCode == LlmErrorCode.LLM_INVALID_CONFIGURATION) {
            return DocumentGenerationFailureCause.AUTHENTICATION;
        }
        if (errorCode == LlmErrorCode.LLM_INPUT_LIMIT_EXCEEDED) {
            return DocumentGenerationFailureCause.INPUT_LIMIT;
        }
        if (errorCode == LlmErrorCode.LLM_OUTPUT_LIMIT_EXCEEDED) {
            return DocumentGenerationFailureCause.OUTPUT_LIMIT;
        }
        if (errorCode == LlmErrorCode.LLM_INVALID_RESPONSE) {
            return DocumentGenerationFailureCause.INVALID_RESPONSE;
        }
        return DocumentGenerationFailureCause.INVALID_INPUT;
    }

    private DocumentGenerationFailureCause documentCause(DocumentException exception) {
        if (exception.getErrorCode() == DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_RESPONSE
                || exception.getErrorCode() == DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE) {
            return DocumentGenerationFailureCause.INVALID_RESPONSE;
        }
        return DocumentGenerationFailureCause.INVALID_INPUT;
    }

    private void fail(
            DocumentGenerationExecution execution,
            DocumentGenerationFailureCause cause
    ) {
        log.warn(
                "Document generation failed jobId={} stage={} attempt={} cause={}",
                execution.jobId(),
                execution.stage(),
                execution.attemptCount(),
                cause
        );
        try {
            failures.failAttempt(
                    execution.workspaceId(),
                    execution.jobId(),
                    execution.attemptCount(),
                    cause
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Document generation failure persistence deferred jobId={} attempt={}",
                    execution.jobId(),
                    execution.attemptCount()
            );
        }
    }
}
