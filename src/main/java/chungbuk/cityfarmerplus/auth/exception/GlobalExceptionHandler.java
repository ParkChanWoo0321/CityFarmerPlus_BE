package chungbuk.cityfarmerplus.auth.exception;

import chungbuk.cityfarmerplus.auth.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("입력값이 올바르지 않습니다.");

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest() {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "요청 본문이 올바르지 않습니다."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("입력값이 올바르지 않습니다.");

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "DATA_CONFLICT",
                        "이미 등록된 데이터와 충돌합니다."
                ));
    }
}
