package com.shane.orchestragent.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * OrchestrAgent 统一启动入口
 *
 * @author Shane
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.shane.orchestragent"})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
