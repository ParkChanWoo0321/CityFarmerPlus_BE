package chungbuk.cityfarmerplus.work.guide;

import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.service.WorkAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkGuideService {

    private final WorkAssignmentService assignmentService;
    private final WorkGuideGenerator generator;

    public WorkGuideResponse get(Long userId, Long assignmentId) {
        WorkAssignment assignment = assignmentService
                .getUrbanFarmerOwnedEntity(userId, assignmentId);
        return generator.generate(assignment);
    }
}
