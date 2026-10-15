package com.emi.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Runs @Async methods (notifications, reminders, file uploads) on a bounded pool
 * and logs failures from void @Async methods, which would otherwise be swallowed.
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("emi-async-");
        // Queue full: run on the caller's thread instead of dropping the task
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        // Carry the logged-in user's SecurityContext into the async thread
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    // Only invoked for void @Async methods; CompletableFuture-returning methods surface errors through the future.
    // Params are not logged: they can carry phone numbers, passwords or KYC data.
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Async method {}.{} failed",
                method.getDeclaringClass().getSimpleName(), method.getName(), ex);
    }
}
