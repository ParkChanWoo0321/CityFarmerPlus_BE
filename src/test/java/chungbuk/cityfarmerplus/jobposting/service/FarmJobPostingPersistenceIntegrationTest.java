package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingDetails;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class FarmJobPostingPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FarmProfileRepository farmProfileRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private FarmJobPostingService farmJobPostingService;

    @Test
    void listsEmptyPostingsForDraftFarmWhenDisplayStatusIsOmitted() {
        User farmUser = saveDraftFarm("farm_posting_list_empty").getOwner();

        var response = farmJobPostingService.getMine(
                farmUser.getId(),
                null,
                0,
                20
        );

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void listsPersistedPostingForDraftFarmWhenDisplayStatusIsOmitted() {
        FarmProfile farmProfile = saveDraftFarm("farm_posting_list_one");
        JobPosting posting = jobPostingRepository.save(JobPosting.createDraft(
                farmProfile,
                new JobPostingDetails(
                        "감자",
                        "수확",
                        LocalDate.of(2026, 9, 1),
                        LocalTime.of(9, 0),
                        LocalTime.of(17, 0),
                        4,
                        "농장 입구",
                        120_000,
                        JobPosting.WageUnit.DAILY,
                        "장갑",
                        "안전화 착용",
                        "함께 일해요",
                        "초보자 환영",
                        "감자 수확 도우미",
                        "감자 수확 작업자를 모집합니다.",
                        "농가 안내에 따라주세요."
                )
        ));

        var response = farmJobPostingService.getMine(
                farmProfile.getOwner().getId(),
                null,
                0,
                20
        );

        assertThat(response.content()).singleElement()
                .extracting("id")
                .isEqualTo(posting.getId());
        assertThat(response.totalElements()).isOne();
    }

    private FarmProfile saveDraftFarm(String loginId) {
        User farmUser = userRepository.save(User.register(
                loginId,
                "encoded",
                "농가 사용자",
                User.UserType.FARM
        ));
        return farmProfileRepository.save(FarmProfile.createDraft(
                farmUser,
                "테스트 농가",
                "대표자",
                "01012345678",
                "충청북도 청주시",
                ChungbukCityCounty.CHEONGJU,
                List.of("감자"),
                "감자 재배",
                "1234567890",
                100
        ));
    }
}
