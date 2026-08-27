package com.knot.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class KnotApplicationTests {
    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // given
        ConfigurableApplicationContext context = applicationContext;

        // when
        boolean active = context.isActive();

        // then
        assertTrue(active);
    }
}
