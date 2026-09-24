package com.supporttickets.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.supporttickets.domain.DomainErrorCodes;
import com.supporttickets.domain.IllegalStatusTransitionException;
import com.supporttickets.domain.InvalidRequestException;
import com.supporttickets.domain.InvalidStatusValueException;
import com.supporttickets.domain.StatusNotUpdatableException;
import com.supporttickets.domain.TicketNotFoundException;

import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String INVALID_REQUEST_MESSAGE = "Request is invalid.";
    private static final String INVALID_BODY_MESSAGE = "Request body is invalid.";

    @ExceptionHandler(IllegalStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse> illegalTransition(IllegalStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        ex.code(),
                        ex.getMessage(),
                        ex.currentStatus(),
                        ex.requestedStatus()
                ));
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(TicketNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidStatusValueException.class)
    public ResponseEntity<ApiErrorResponse> invalidStatus(InvalidStatusValueException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> invalidRequest(InvalidRequestException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(StatusNotUpdatableException.class)
    public ResponseEntity<ApiErrorResponse> statusNotUpdatable(StatusNotUpdatableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            BindException.class
    })
    public ResponseEntity<ApiErrorResponse> validationFailure() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(DomainErrorCodes.INVALID_REQUEST, INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            ConversionFailedException.class,
            MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> malformedRequest() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(DomainErrorCodes.INVALID_REQUEST, INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadable() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(DomainErrorCodes.INVALID_REQUEST, INVALID_BODY_MESSAGE));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> optimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict on ticket write");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(null, "The ticket was updated by another request.", null, null));
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class})
    public ResponseEntity<ApiErrorResponse> persistenceFailure(Exception ex) {
        log.error("Persistence failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.unexpected());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
        log.error("Unexpected server failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.unexpected());
    }
}
