package com.example.coach.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real PostgreSQL for tests and local runs. Uses Testcontainers when Docker is available (CI) and falls back to
 * an embedded PostgreSQL binary otherwise, so the suite also runs on machines without Docker.
 * Force one with {@code TEST_DB=docker} or {@code TEST_DB=embedded}.
 */
public final class TestDatabase {

    public record Info(String url, String username, String password, String kind) {}

    private static Info shared;

    private TestDatabase() {}

    /** One database for the whole JVM; Flyway migrates it once per Spring context. */
    public static synchronized Info shared() {
        if (shared == null) {
            shared = start(null);
        }
        return shared;
    }

    public static void register(DynamicPropertyRegistry registry) {
        Info db = shared();
        registry.add("spring.datasource.url", db::url);
        registry.add("spring.datasource.username", db::username);
        registry.add("spring.datasource.password", db::password);
    }

    /** Starts a database; with {@code dataDirectory} the embedded variant keeps its data between runs. */
    public static Info start(Path dataDirectory) {
        String mode = System.getenv().getOrDefault("TEST_DB", "auto");
        if (!"embedded".equals(mode) && ("docker".equals(mode) || dockerAvailable())) {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:17-alpine");
            container.start(); // stopped by Testcontainers' reaper when the JVM exits
            return new Info(container.getJdbcUrl(), container.getUsername(), container.getPassword(), "testcontainers");
        }
        try {
            EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
            if (dataDirectory != null) {
                builder.setDataDirectory(dataDirectory).setCleanDataDirectory(false);
            }
            EmbeddedPostgres postgres = builder.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // shutting down anyway
                }
            }));
            return new Info(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "postgres", "embedded");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }
}
