package chungbuk.cityfarmerplus.farm.exception;

import chungbuk.cityfarmerplus.auth.dto.ErrorResponse;
import chungbuk.cityfarmerplus.application.controller.FarmCandidateController;
import chungbuk.cityfarmerplus.farm.controller.FarmProfileController;
import chungbuk.cityfarmerplus.farm.ownership.controller.FarmOwnershipSubmissionController;
import chungbuk.cityfarmerplus.farm.ownership.controller.FarmOwnershipDocumentController;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.jobposting.controller.FarmJobPostingController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        FarmCandidateController.class,
        FarmJobPostingController.class,
        FarmProfileController.class,
        FarmOwnershipSubmissionController.class,
        FarmOwnershipDocumentController.class
})
public class FarmProfileExceptionHandler {

    @ExceptionHandler(FarmProfileException.class)
    public ResponseEntity<ErrorResponse> handleFarmProfileException(
            FarmProfileException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(FarmOwnershipException.class)
    public ResponseEntity<ErrorResponse> handleFarmOwnershipException(
            FarmOwnershipException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }
}
