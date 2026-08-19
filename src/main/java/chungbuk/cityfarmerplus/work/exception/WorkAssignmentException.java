package chungbuk.cityfarmerplus.work.exception;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class WorkAssignmentException extends DomainException {

    private WorkAssignmentException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static WorkAssignmentException notFound() {
        return new WorkAssignmentException(HttpStatus.NOT_FOUND, "WORK_ASSIGNMENT_NOT_FOUND", "근무 일정을 찾을 수 없습니다.");
    }

    public static WorkAssignmentException notOwner() {
        return new WorkAssignmentException(HttpStatus.FORBIDDEN, "WORK_ASSIGNMENT_NOT_OWNER", "본인과 관련된 근무 일정만 처리할 수 있습니다.");
    }

    public static WorkAssignmentException invalidState(String message) {
        return new WorkAssignmentException(HttpStatus.CONFLICT, "INVALID_WORK_ASSIGNMENT_STATE", message);
    }
}
