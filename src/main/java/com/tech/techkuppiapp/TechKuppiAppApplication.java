package com.tech.techkuppiapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
//@EnableScheduling
@ComponentScan(basePackages  = "com.tech.techkuppiapp")
public class TechKuppiAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechKuppiAppApplication.class, args);
    }

}
