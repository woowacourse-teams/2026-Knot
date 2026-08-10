package com.knot.backend;

import com.knot.backend.testSupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class KnotApplicationTests {

    @Test
    void contextLoads() {}
}
