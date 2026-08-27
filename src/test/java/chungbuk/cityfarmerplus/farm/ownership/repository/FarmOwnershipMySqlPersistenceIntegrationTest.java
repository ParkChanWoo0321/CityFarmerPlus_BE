package chungbuk.cityfarmerplus.farm.ownership.repository;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("mysql-integration")
@Tag("mysql-integration")
class FarmOwnershipMySqlPersistenceIntegrationTest extends FarmOwnershipPersistenceContract {
}
