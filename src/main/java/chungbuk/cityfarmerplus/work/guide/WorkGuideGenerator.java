package chungbuk.cityfarmerplus.work.guide;

import chungbuk.cityfarmerplus.work.entity.WorkAssignment;

public interface WorkGuideGenerator {

    WorkGuideResponse generate(WorkAssignment assignment);
}
