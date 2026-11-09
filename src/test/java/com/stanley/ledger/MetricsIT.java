package com.stanley.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.outbox.OutboxRelay;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

class MetricsIT extends IntegrationBase {
  @Value("${local.server.port}")
  int port;

  @Autowired OutboxRelay relay;

  final HttpClient http = HttpClient.newHttpClient();

  String get(String path) throws Exception {
    return http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString())
        .body();
  }

  @Test
  void sliMetricsAreExposedAfterTransfers() throws Exception {
    List<UUID> ids = fundedAccounts(2, 100_000);
    String body =
        "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinor\":100}"
            .formatted(ids.get(1), ids.get(2));
    for (int i = 0; i < 5; i++) {
      var req =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/transfers"))
              .header("Content-Type", "application/json")
              .header("X-Api-Key", "dev-key-local-only")
              .header("Idempotency-Key", "metrics-" + i)
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      assertThat(http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
    }
    long deadline = System.currentTimeMillis() + 60_000;
    String metrics = "";
    while (System.currentTimeMillis() < deadline) {
      relay.pollOnce();
      metrics = get("/actuator/prometheus");
      if (metrics.contains("ledger_projection_lag_seconds_bucket")) {
        break;
      }
      Thread.sleep(500);
    }
    assertThat(metrics).contains("ledger_projection_lag_seconds_bucket");
    assertThat(metrics).contains("http_server_requests_seconds_bucket{");
    assertThat(metrics).contains("uri=\"/transfers\"");
  }
}
