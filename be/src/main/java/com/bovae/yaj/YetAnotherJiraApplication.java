package com.bovae.yaj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YetAnotherJiraApplication {

    public static void main(String[] args) {
        SpringApplication.run(YetAnotherJiraApplication.class, args);
    }
}
