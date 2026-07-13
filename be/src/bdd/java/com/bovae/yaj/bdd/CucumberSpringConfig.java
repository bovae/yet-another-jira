package com.bovae.yaj.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestcontainersConfig.registerProperties(registry);
    }
}
