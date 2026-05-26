package com.zsubera.jpa.spec;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackageClasses = TestApplication.class)
@EnableJpaRepositories(basePackageClasses = TestApplication.class)
public class TestApplication {}
