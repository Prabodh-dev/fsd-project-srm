package dev.prabodh.examedge.support;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    public Clock clockAlias(MutableClock mutableClock) {
        return mutableClock;
    }
}
