package com.example.coach;

import com.example.coach.support.TestDatabase;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * Runs the app locally with a throwaway PostgreSQL (Docker if available, else an embedded binary), so no
 * database setup is needed:
 *
 * <pre>./mvnw spring-boot:test-run</pre>
 *
 * Data is kept in target/dev-db between runs (deleted by {@code mvn clean}).
 */
public final class TestCoachApplication {

    private TestCoachApplication() {}

    public static void main(String[] args) {
        if (System.getenv("DB_URL") == null) {
            TestDatabase.Info db = TestDatabase.start(Path.of("target", "dev-db"));
            System.setProperty("spring.datasource.url", db.url());
            System.setProperty("spring.datasource.username", db.username());
            System.setProperty("spring.datasource.password", db.password());
            System.out.println("Using " + db.kind() + " PostgreSQL at " + db.url());
        }
        SpringApplication.from(CoachApplication::main).run(args);
    }
}
