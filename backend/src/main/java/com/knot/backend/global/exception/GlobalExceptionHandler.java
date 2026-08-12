package com.knot.backend.global.exception;

import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.global.response.FieldErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<ErrorResponse> handleProjectException(ProjectException exception) {
        return respond(exception.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fieldErrors =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new FieldErrorResponse(
                                                error.getField(), error.getDefaultMessage()))
                        .toList();

        return respond(CommonErrorCode.VALIDATION_ERROR, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception) {
        List<FieldErrorResponse> fieldErrors =
                exception.getConstraintViolations().stream()
                        .map(this::toFieldErrorResponse)
                        .toList();

        return respond(CommonErrorCode.VALIDATION_ERROR, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ignored) {
        return respond(CommonErrorCode.INVALID_REQUEST_BODY);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        FieldErrorResponse fieldError =
                new FieldErrorResponse(
                        exception.getName(), CommonErrorCode.INVALID_PARAMETER.getMessage());

        return respond(CommonErrorCode.INVALID_PARAMETER, List.of(fieldError));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception) {

        FieldErrorResponse fieldError =
                new FieldErrorResponse(
                        exception.getParameterName(),
                        CommonErrorCode.MISSING_PARAMETER.getMessage());

        return respond(CommonErrorCode.MISSING_PARAMETER, List.of(fieldError));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ignored) {
        return respond(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private FieldErrorResponse toFieldErrorResponse(ConstraintViolation<?> violation) {
        return new FieldErrorResponse(
                extractFieldName(violation.getPropertyPath()), violation.getMessage());
    }

    private String extractFieldName(Path path) {
        return StreamSupport.stream(path.spliterator(), false)
                .filter(this::isFieldNode)
                .map(Path.Node::getName)
                .filter(this::isUsableFieldName)
                .reduce((first, second) -> second)
                .orElse("request");
    }

    private boolean isFieldNode(Path.Node node) {
        return node.getKind() == ElementKind.PROPERTY || node.getKind() == ElementKind.PARAMETER;
    }

    private boolean isUsableFieldName(String fieldName) {
        return fieldName != null && !fieldName.matches("arg\\d+");
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode) {
        return respond(errorCode, List.of());
    }

    private ResponseEntity<ErrorResponse> respond(
            ErrorCode errorCode, List<FieldErrorResponse> fieldErrors) {
        return ResponseEntity.status(toHttpStatus(errorCode.getCategory()))
                .body(new ErrorResponse(errorCode, fieldErrors));
    }

    private HttpStatus toHttpStatus(ErrorCategory category) {
        return switch (category) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
