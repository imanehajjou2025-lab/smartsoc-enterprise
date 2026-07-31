package com.smartsoc.api.settings;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class AiHealthCheckerTest {

    private final AiHealthChecker checker = new AiHealthChecker();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void checkReturnsNotConfiguredWhenNoUrlIsSet() {
        assertThat(checker.check(null, "/health")).isEqualTo(AiHealthChecker.Status.NOT_CONFIGURED);
        assertThat(checker.check(" ", "/health")).isEqualTo(AiHealthChecker.Status.NOT_CONFIGURED);
    }

    @Test
    void checkReturnsDownWhenTheServiceIsUnreachable() {
        assertThat(checker.check("http://localhost:1", "/health")).isEqualTo(AiHealthChecker.Status.DOWN);
    }

    @Test
    void checkReturnsUpWhenTheServiceRespondsSuccessfully() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        AiHealthChecker.Status status = checker.check(
                "http://localhost:" + server.getAddress().getPort(), "/health");

        assertThat(status).isEqualTo(AiHealthChecker.Status.UP);
    }

    @Test
    void checkReturnsDownWhenTheServiceRespondsWithAnError() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        server.start();

        AiHealthChecker.Status status = checker.check(
                "http://localhost:" + server.getAddress().getPort(), "/health");

        assertThat(status).isEqualTo(AiHealthChecker.Status.DOWN);
    }
}
