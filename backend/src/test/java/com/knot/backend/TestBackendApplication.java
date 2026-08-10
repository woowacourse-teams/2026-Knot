package com.knot.backend;

import com.knot.backend.testSupport.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(KnotApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
