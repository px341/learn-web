package com.learn.mistakeservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.learn.mistakeservice.mapper")
public class MistakeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MistakeServiceApplication.class, args);
    }

}
