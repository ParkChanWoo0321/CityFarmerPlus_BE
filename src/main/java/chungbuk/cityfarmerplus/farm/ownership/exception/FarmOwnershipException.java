package chungbuk.cityfarmerplus.farm.ownership.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FarmOwnershipException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private FarmOwnershipException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static FarmOwnershipException documentsRequired() {
        return new FarmOwnershipException(
                HttpStatus.BAD_REQUEST,
                "OWNERSHIP_DOCUMENTS_REQUIRED",
                "농가 소유 증빙 파일을 한 개 이상 첨부해야 합니다."
        );
    }

    public static FarmOwnershipException tooManyDocuments() {
        return new FarmOwnershipException(
                HttpStatus.BAD_REQUEST,
                "TOO_MANY_OWNERSHIP_DOCUMENTS",
                "농가 소유 증빙 파일은 최대 5개까지 첨부할 수 있습니다."
        );
    }

    public static FarmOwnershipException documentTooLarge() {
        return new FarmOwnershipException(
                HttpStatus.CONTENT_TOO_LARGE,
                "OWNERSHIP_DOCUMENT_TOO_LARGE",
                "농가 소유 증빙 파일은 개별 파일당 10MB 이하여야 합니다."
        );
    }

    public static FarmOwnershipException totalSizeTooLarge() {
        return new FarmOwnershipException(
                HttpStatus.CONTENT_TOO_LARGE,
                "OWNERSHIP_DOCUMENTS_TOTAL_SIZE_TOO_LARGE",
                "농가 소유 증빙 파일의 전체 크기는 30MB 이하여야 합니다."
        );
    }

    public static FarmOwnershipException invalidFilename() {
        return new FarmOwnershipException(
                HttpStatus.BAD_REQUEST,
                "INVALID_OWNERSHIP_DOCUMENT_FILENAME",
                "농가 소유 증빙 파일명이 올바르지 않습니다."
        );
    }

    public static FarmOwnershipException unsupportedDocumentType() {
        return new FarmOwnershipException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_OWNERSHIP_DOCUMENT_TYPE",
                "농가 소유 증빙은 PDF, JPG, JPEG, PNG 파일만 첨부할 수 있습니다."
        );
    }

    public static FarmOwnershipException invalidDocumentContent() {
        return new FarmOwnershipException(
                HttpStatus.BAD_REQUEST,
                "INVALID_OWNERSHIP_DOCUMENT_CONTENT",
                "파일의 실제 내용이 확장자 또는 파일 형식과 일치하지 않습니다."
        );
    }

    public static FarmOwnershipException submissionNotAllowed() {
        return new FarmOwnershipException(
                HttpStatus.CONFLICT,
                "OWNERSHIP_SUBMISSION_NOT_ALLOWED",
                "현재 농가 프로필 상태에서는 소유 증빙을 제출할 수 없습니다."
        );
    }

    public static FarmOwnershipException dataConflict() {
        return new FarmOwnershipException(
                HttpStatus.CONFLICT,
                "OWNERSHIP_SUBMISSION_DATA_CONFLICT",
                "농가 소유 증빙 제출 정보가 기존 데이터와 충돌합니다."
        );
    }

    public static FarmOwnershipException storageFailure() {
        return new FarmOwnershipException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "OWNERSHIP_DOCUMENT_STORAGE_ERROR",
                "농가 소유 증빙 파일을 저장하지 못했습니다."
        );
    }

    public static FarmOwnershipException submissionNotFound() {
        return new FarmOwnershipException(
                HttpStatus.NOT_FOUND,
                "OWNERSHIP_SUBMISSION_NOT_FOUND",
                "농가 소유 증빙 제출을 찾을 수 없습니다."
        );
    }

    public static FarmOwnershipException documentNotFound() {
        return new FarmOwnershipException(
                HttpStatus.NOT_FOUND,
                "OWNERSHIP_DOCUMENT_NOT_FOUND",
                "농가 소유 증빙 파일을 찾을 수 없습니다."
        );
    }

    public static FarmOwnershipException documentAccessDenied() {
        return new FarmOwnershipException(
                HttpStatus.FORBIDDEN,
                "OWNERSHIP_DOCUMENT_ACCESS_DENIED",
                "이 농가 소유 증빙 파일을 조회할 권한이 없습니다."
        );
    }

    public static FarmOwnershipException documentReadFailure() {
        return new FarmOwnershipException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "OWNERSHIP_DOCUMENT_READ_ERROR",
                "농가 소유 증빙 파일을 읽지 못했습니다."
        );
    }

    public static FarmOwnershipException reviewNotAllowed() {
        return new FarmOwnershipException(
                HttpStatus.CONFLICT,
                "OWNERSHIP_REVIEW_NOT_ALLOWED",
                "심사 대기 중인 최신 소유 증빙 제출만 처리할 수 있습니다."
        );
    }

    public static FarmOwnershipException rejectionReasonRequired() {
        return new FarmOwnershipException(
                HttpStatus.BAD_REQUEST,
                "OWNERSHIP_REJECTION_REASON_REQUIRED",
                "반려 사유는 필수입니다."
        );
    }
}
