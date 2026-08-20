package chungbuk.cityfarmerplus.education.repository;

import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class EducationCertificateSubmissionRepositoryContractTest {

    @Test
    void reviewQueriesExcludeInactiveAccounts() throws NoSuchMethodException {
        Method list = EducationCertificateSubmissionRepository.class.getMethod(
                "findAllByStatus",
                EducationCertificateSubmission.SubmissionStatus.class,
                Pageable.class
        );
        Method detailForUpdate = EducationCertificateSubmissionRepository.class
                .getMethod("findByIdForUpdate", Long.class);
        Method count = EducationCertificateSubmissionRepository.class.getMethod(
                "countByStatus",
                EducationCertificateSubmission.SubmissionStatus.class
        );

        assertActiveAccountFilter(list);
        assertActiveAccountFilter(detailForUpdate);
        assertActiveAccountFilter(count);
    }

    private void assertActiveAccountFilter(Method method) {
        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("certification.urbanFarmer.accountStatus")
                .contains("AccountStatus.ACTIVE");
    }
}
