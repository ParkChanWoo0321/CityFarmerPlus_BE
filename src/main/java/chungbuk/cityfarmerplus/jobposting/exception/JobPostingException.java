package chungbuk.cityfarmerplus.jobposting.exception;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class JobPostingException extends DomainException {

    private JobPostingException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static JobPostingException farmApprovalRequired() {
        return new JobPostingException(HttpStatus.FORBIDDEN, "FARM_APPROVAL_REQUIRED", "승인된 농가만 지원자와 근무를 관리할 수 있습니다.");
    }

    public static JobPostingException notFound() {
        return new JobPostingException(HttpStatus.NOT_FOUND, "JOB_POSTING_NOT_FOUND", "모집 공고를 찾을 수 없습니다.");
    }

    public static JobPostingException notOwner() {
        return new JobPostingException(HttpStatus.FORBIDDEN, "JOB_POSTING_NOT_OWNER", "본인 농가의 모집 공고만 관리할 수 있습니다.");
    }

    public static JobPostingException invalidState(String message) {
        return new JobPostingException(HttpStatus.CONFLICT, "INVALID_JOB_POSTING_STATE", message);
    }

    public static JobPostingException pastWorkDate() {
        return new JobPostingException(HttpStatus.BAD_REQUEST, "PAST_WORK_DATE", "작업 시작 시각은 현재 이후여야 합니다.");
    }

    public static JobPostingException invalidDetails(String message) {
        return new JobPostingException(HttpStatus.BAD_REQUEST, "INVALID_JOB_POSTING_DETAILS", message);
    }

    public static JobPostingException notOpen() {
        return new JobPostingException(HttpStatus.NOT_FOUND, "JOB_POSTING_NOT_OPEN", "현재 공개 중인 모집 공고를 찾을 수 없습니다.");
    }

    public static JobPostingException activeMatchesExist() {
        return new JobPostingException(HttpStatus.CONFLICT, "ACTIVE_MATCHES_EXIST", "확정된 근무자가 있는 공고는 농가가 취소할 수 없습니다.");
    }

    public static JobPostingException capacityBelowMatchedCount() {
        return new JobPostingException(HttpStatus.CONFLICT, "CAPACITY_BELOW_MATCHED_COUNT", "이미 확정된 인원보다 모집 인원을 적게 수정할 수 없습니다.");
    }

    public static JobPostingException matchedPostingUpdateNotAllowed() {
        return new JobPostingException(
                HttpStatus.CONFLICT,
                "MATCHED_POSTING_UPDATE_NOT_ALLOWED",
                "이미 확정된 근무자가 있는 공고의 근무 조건은 수정할 수 없습니다."
        );
    }
}
