package com.openclaw.grading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * OpenClaw 智能作业批改系统主应用
 */
@SpringBootApplication
@EnableAsync
public class OpenClawGradingApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(OpenClawGradingApplication.class, args);
    }
}
