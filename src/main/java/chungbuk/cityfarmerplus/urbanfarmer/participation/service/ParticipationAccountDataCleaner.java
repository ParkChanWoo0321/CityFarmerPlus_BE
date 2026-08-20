package chungbuk.cityfarmerplus.urbanfarmer.participation.service;

import chungbuk.cityfarmerplus.auth.service.AccountDataCleaner;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(10)
@RequiredArgsConstructor
public class ParticipationAccountDataCleaner implements AccountDataCleaner {

    private final ParticipationApplicationRepository applicationRepository;

    @Override
    public void clean(Long userId) {
        Instant cancelledAt = Instant.now();
        applicationRepository.findAllByUrbanFarmerIdForUpdate(userId)
                .forEach(application ->
                        application.cancelForAccountWithdrawal(cancelledAt));
    }
}
