package com.femstep.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FemstepStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FemstepStoreApplication.class, args);
    }

}
