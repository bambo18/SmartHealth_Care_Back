package com.smarthealthdog.backend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AppConfig {

    // 현재는 개발 환경이므로 스레드 풀 크기를 작게 설정
    // 운영 환경에서는 시스템 리소스에 맞게 조정 필요
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        // executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("MyExecutor-");

        return executor;
    }
}