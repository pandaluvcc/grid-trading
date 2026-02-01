package com.gridtrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.gridtrading.repository")
public class GridTradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GridTradingApplication.class, args);
    }

}
