package com.eduerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "EduERP", sharedModules = {})
@SpringBootApplication
public class EduErpApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduErpApplication.class, args);
    }
}
