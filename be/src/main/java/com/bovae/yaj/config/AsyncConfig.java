package com.bovae.yaj.config;

import com.bovae.yaj.support.MdcTaskDecorator;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /** Bean name of the executor that runs verification-email dispatch off the request thread. */
    public static final String VERIFICATION_EMAIL_EXECUTOR = "verificationEmailExecutor";

    @Bean(VERIFICATION_EMAIL_EXECUTOR)
    public Executor verificationEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("verify-email-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
