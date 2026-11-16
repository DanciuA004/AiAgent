package com.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Boot application.
 */
@SpringBootApplication
public class App {

    /**
     * Starts the application and initializes the Spring context.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        // Spring Boot discovers application components and starts the embedded web server.
        SpringApplication.run(App.class, args);

    }
}
