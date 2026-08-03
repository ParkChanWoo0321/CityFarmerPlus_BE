package chungbuk.cityfarmerplus.farm.exception;

import chungbuk.cityfarmerplus.auth.dto.ErrorResponse;
import chungbuk.cityfarmerplus.farm.controller.FarmProfileController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FarmProfileController.class)
public class FarmProfileExceptionHandler {

    @ExceptionHandler(FarmProfileException.class)
    public ResponseEntity<ErrorResponse> handleFarmProfileException(
            FarmProfileException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }
}
