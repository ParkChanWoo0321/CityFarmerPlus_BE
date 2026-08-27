package chungbuk.cityfarmerplus.farm.ownership.repository;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class FarmOwnershipPersistenceIntegrationTest extends FarmOwnershipPersistenceContract {
}
