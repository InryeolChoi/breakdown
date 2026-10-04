package com.breakground.config;

import com.breakground.chat.ChatProperties;
import com.breakground.report.ReportProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ChatProperties.class, ReportProperties.class})
public class ChatConfiguration {
    @Bean
    public Clock applicationClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
