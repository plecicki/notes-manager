package pl.betacom.plecicki.notesmanager.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        return validationError(HttpStatus.BAD_REQUEST, "error.validation.failed", fields);
    }

    @ExceptionHandler(LoginAlreadyTakenException.class)
    public ResponseEntity<ErrorResponse> handleLoginTaken(LoginAlreadyTakenException ex) {
        return error(HttpStatus.CONFLICT, "error.login.taken", ex.getLogin());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, "error.bad.credentials");
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleItemNotFound(ItemNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "error.item.not.found");
    }

    @ExceptionHandler(ItemAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(ItemAccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "error.forbidden");
    }

    @ExceptionHandler(ItemVersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(ItemVersionConflictException ex) {
        String message = messageSource.getMessage("error.item.version.conflict", null, LocaleContextHolder.getLocale());
        Map<String, String> fields = Map.of("currentVersion", String.valueOf(ex.getCurrentVersion()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.getReasonPhrase(), message, fields));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, "error.item.version.conflict");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, Object... args) {
        String message = messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.getReasonPhrase(), message));
    }

    private ResponseEntity<ErrorResponse> validationError(HttpStatus status, String code, Map<String, String> fields) {
        String message = messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.getReasonPhrase(), message, fields));
    }
}