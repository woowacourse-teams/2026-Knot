package com.knot.backend;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class KnotApplicationTests {

    @Test
    void contextLoads() {
        // given

        // when

        // then
    }
}
