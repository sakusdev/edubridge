package dev.sakus.geyseredu.authservice;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HealthCheckMain {
    private HealthCheckMain() {
    }

    public static void main(String[] args) throws Exception {
        URI uri = URI.create(args.length == 0 ? "http://127.0.0.1:8080/health/ready" : args[0]);
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && response.body().contains("\"ready\":true")) {
            return;
        }
        System.err.println("auth-service is not ready: HTTP " + response.statusCode() + " " + response.body());
        System.exit(1);
    }
}
