package dev.skim.caffeinatedredis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test application for Near Cache library.
 * When used as a library, the AutoConfiguration will be triggered instead of this class.
 */
@SpringBootApplication
public class CaffeinatedRedisApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaffeinatedRedisApplication.class, args);
    }

}
