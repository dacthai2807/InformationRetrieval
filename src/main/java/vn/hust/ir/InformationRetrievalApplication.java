package vn.hust.ir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Information Retrieval application.
 *
 * Spring Boot automatically discovers components under vn.hust.ir,
 * including controllers, services and repositories added later.
 */
@SpringBootApplication
public class InformationRetrievalApplication {

    public static void main(String[] args) {
        SpringApplication.run(InformationRetrievalApplication.class, args);
    }
}
