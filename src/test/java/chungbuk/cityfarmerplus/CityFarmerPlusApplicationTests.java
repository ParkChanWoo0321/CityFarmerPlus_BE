package chungbuk.cityfarmerplus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".{32,}")
class CityFarmerPlusApplicationTests {

    @Test
    void contextLoads() {
    }

}
