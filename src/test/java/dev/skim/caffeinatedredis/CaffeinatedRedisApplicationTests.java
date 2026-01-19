package dev.skim.caffeinatedredis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Basic Spring Boot context smoke test.
 *
 * This project is a starter/library and does not require Redis for a minimal application context.
 * We explicitly disable near-cache auto-configuration here to keep the test stable.
 */
@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "near-cache.enabled=false"
})
class CaffeinatedRedisApplicationTests {

    @Test
    void contextLoads() {
    }
}
