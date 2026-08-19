package chungbuk.cityfarmerplus.dashboard.service;

import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingAccessService;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingResponseAssembler;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmHomeServiceTest {

    @Mock
    private JobPostingAccessService accessService;
    @Mock
    private JobPostingRepository postingRepository;
    @Mock
    private WorkAssignmentRepository assignmentRepository;
    @Mock
    private JobPostingResponseAssembler postingResponseAssembler;

    private FarmHomeService service;

    @BeforeEach
    void setUp() {
        service = new FarmHomeService(
                accessService,
                postingRepository,
                assignmentRepository,
                postingResponseAssembler
        );
    }

    @Test
    void countsOnlyCurrentlyAcceptingOpenPostingsForFarmHome() {
        FarmProfile farm = mock(FarmProfile.class);
        when(farm.getId()).thenReturn(10L);
        when(farm.getCrops()).thenReturn(List.of());
        when(farm.getStatus()).thenReturn(FarmProfile.FarmProfileStatus.APPROVED);
        when(accessService.requireFarmProfile(1L)).thenReturn(farm);
        when(postingRepository.countCurrentlyOpenByFarmOwnerId(
                eq(1L), any(LocalDate.class), any(LocalTime.class)
        )).thenReturn(3L);
        when(postingRepository.countExpiredOpenByFarmOwnerId(
                eq(1L), any(LocalDate.class), any(LocalTime.class)
        )).thenReturn(2L);
        when(postingRepository.countByFarmProfileOwnerIdAndStatus(
                eq(1L), any(JobPosting.JobPostingStatus.class)
        )).thenReturn(1L);
        when(postingRepository.findTop5ByFarmProfileOwnerIdOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of());
        when(postingRepository.count(any(Specification.class))).thenReturn(2L);
        when(postingResponseAssembler.assembleAll(List.of())).thenReturn(List.of());
        when(assignmentRepository
                .findUpcomingByFarmProfileId(
                        eq(10L),
                        any(LocalDate.class),
                        any(LocalTime.class),
                        any(Pageable.class)
                )).thenReturn(List.of());

        var response = service.get(1L);

        assertThat(response.postingCounts().get(JobPosting.JobPostingStatus.OPEN.name()))
                .isEqualTo(1L);
        assertThat(response.postingCounts().get(JobPosting.JobPostingStatus.DRAFT.name()))
                .isEqualTo(1L);
        assertThat(response.displayPostingCounts()).containsEntry("APPROVED", 3L);
        assertThat(response.displayPostingCounts()).containsEntry("CLOSED", 4L);
        assertThat(response.displayPostingCounts()).containsEntry("REJECTED", 2L);
        verify(postingRepository).countByFarmProfileOwnerIdAndStatus(
                1L,
                JobPosting.JobPostingStatus.OPEN
        );
        verify(assignmentRepository).findUpcomingByFarmProfileId(
                eq(10L),
                any(LocalDate.class),
                any(LocalTime.class),
                any(Pageable.class)
        );
    }
}
