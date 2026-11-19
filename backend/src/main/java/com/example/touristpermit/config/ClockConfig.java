package com.example.touristpermit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** Nepal Standard Time is used for calendar-date decisions (permit validity days). */
    public static final ZoneId PERMIT_ZONE = ZoneId.of("Asia/Kathmandu");

    @Bean
    Clock clock() {
        return Clock.system(PERMIT_ZONE);
    }
}
