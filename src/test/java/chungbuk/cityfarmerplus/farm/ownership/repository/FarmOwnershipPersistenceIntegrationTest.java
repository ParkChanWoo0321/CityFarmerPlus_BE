package chungbuk.cityfarmerplus.farm.ownership.repository;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "true"
)
class FarmOwnershipPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FarmProfileRepository farmProfileRepository;

    @Autowired
    private FarmOwnershipSubmissionRepository submissionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void mysqlPersistsSubmissionDocumentsAndExecutesProfileLockQuery() {
        String uniqueValue = UUID.randomUUID().toString();
        User owner = userRepository.saveAndFlush(User.register(
                "farm_" + uniqueValue.substring(0, 20),
                "encoded-password",
                "통합 테스트 농가",
                User.UserType.FARM
        ));
        FarmProfile profile = farmProfileRepository.saveAndFlush(
                FarmProfile.createDraft(
                        owner,
                        "통합 테스트 농원",
                        "홍길동",
                        "01012345678",
                        "충청북도 충주시 예시로 1",
                        ChungbukCityCounty.CHUNGJU,
                        List.of("사과"),
                        "소유 증빙 저장 통합 테스트",
                        null
                )
        );
        Long profileId = profile.getId();

        entityManager.clear();
        FarmProfile lockedProfile = farmProfileRepository
                .findByOwnerIdForUpdate(owner.getId())
                .orElseThrow();

        FarmOwnershipSubmission submission =
                FarmOwnershipSubmission.createPending(lockedProfile, 1);
        submission.addDocument(
                "토지대장.pdf",
                "integration/" + uniqueValue + "/first.pdf",
                "application/pdf",
                100L,
                "a".repeat(64)
        );
        submission.addDocument(
                "농지원부.png",
                "integration/" + uniqueValue + "/second.png",
                "image/png",
                200L,
                "b".repeat(64)
        );
        lockedProfile.markOwnershipReviewPending();
        Long submissionId = submissionRepository.saveAndFlush(submission).getId();

        entityManager.clear();
        FarmOwnershipSubmission found = submissionRepository
                .findById(submissionId)
                .orElseThrow();

        assertThat(found.getAttemptNumber()).isEqualTo(1);
        assertThat(found.getDocuments())
                .extracting("originalFilename")
                .containsExactly("토지대장.pdf", "농지원부.png");
        assertThat(submissionRepository.findMaxAttemptNumberByFarmProfileId(profileId))
                .isEqualTo(1);
        assertThat(farmProfileRepository.findById(profileId).orElseThrow().getStatus())
                .isEqualTo(FarmProfile.FarmProfileStatus.PENDING_REVIEW);
    }

    @Test
    void mysqlRejectsDuplicateAttemptNumberForTheSameFarmProfile() {
        String uniqueValue = UUID.randomUUID().toString();
        User owner = userRepository.saveAndFlush(User.register(
                "farm_" + uniqueValue.substring(0, 20),
                "encoded-password",
                "중복 회차 테스트 농가",
                User.UserType.FARM
        ));
        FarmProfile profile = farmProfileRepository.saveAndFlush(
                FarmProfile.createDraft(
                        owner,
                        "중복 회차 테스트 농원",
                        "홍길동",
                        "01012345678",
                        "충청북도 충주시 예시로 1",
                        ChungbukCityCounty.CHUNGJU,
                        List.of("사과"),
                        "제출 회차 UNIQUE 제약 테스트",
                        null
                )
        );

        FarmOwnershipSubmission first =
                FarmOwnershipSubmission.createPending(profile, 1);
        first.addDocument(
                "첫번째.pdf",
                "integration/" + uniqueValue + "/first.pdf",
                "application/pdf",
                100L,
                "a".repeat(64)
        );
        submissionRepository.saveAndFlush(first);

        FarmOwnershipSubmission duplicate =
                FarmOwnershipSubmission.createPending(profile, 1);
        duplicate.addDocument(
                "중복.pdf",
                "integration/" + uniqueValue + "/duplicate.pdf",
                "application/pdf",
                100L,
                "b".repeat(64)
        );

        assertThatThrownBy(() -> submissionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
