package com.learn.mistakeservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class MistakeServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
