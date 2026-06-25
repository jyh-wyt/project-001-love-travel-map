package com.lovetravel.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan("com.lovetravel.server.modules")
@SpringBootApplication
public class LoveTravelServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoveTravelServerApplication.class, args);
    }
}
