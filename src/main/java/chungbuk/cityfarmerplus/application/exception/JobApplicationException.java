package chungbuk.cityfarmerplus.application.exception;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class JobApplicationException extends DomainException {

    private JobApplicationException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static JobApplicationException urbanFarmerRequired() {
        return new JobApplicationException(HttpStatus.FORBIDDEN, "URBAN_FARMER_REQUIRED", "도시농부 권한이 필요합니다.");
    }

    public static JobApplicationException educationApprovalRequired() {
        return new JobApplicationException(HttpStatus.FORBIDDEN, "EDUCATION_APPROVAL_REQUIRED", "교육 인증이 완료되어야 공고에 지원할 수 있습니다.");
    }

    public static JobApplicationException duplicateApplication() {
        return new JobApplicationException(HttpStatus.CONFLICT, "DUPLICATE_JOB_APPLICATION", "이미 지원한 공고입니다.");
    }

    public static JobApplicationException notFound() {
        return new JobApplicationException(HttpStatus.NOT_FOUND, "JOB_APPLICATION_NOT_FOUND", "공고 지원 내역을 찾을 수 없습니다.");
    }

    public static JobApplicationException notOwner() {
        return new JobApplicationException(HttpStatus.FORBIDDEN, "JOB_APPLICATION_NOT_OWNER", "본인의 지원 내역만 조회하거나 취소할 수 있습니다.");
    }

    public static JobApplicationException invalidState(String message) {
        return new JobApplicationException(HttpStatus.CONFLICT, "INVALID_JOB_APPLICATION_STATE", message);
    }

    public static JobApplicationException capacityExceeded() {
        return new JobApplicationException(HttpStatus.CONFLICT, "JOB_POSTING_CAPACITY_EXCEEDED", "모집 인원을 초과하여 매칭할 수 없습니다.");
    }

    public static JobApplicationException overlappingAssignment() {
        return new JobApplicationException(HttpStatus.CONFLICT, "OVERLAPPING_WORK_ASSIGNMENT", "같은 시간대에 이미 확정된 근무가 있습니다.");
    }
}
