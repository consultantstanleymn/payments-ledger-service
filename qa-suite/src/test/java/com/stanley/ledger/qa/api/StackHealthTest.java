package com.stanley.ledger.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.config.QaConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/** Fails fast when the local stack is not running. */
class StackHealthTest {
  private static final String DOWN = "stack is down: run podman-compose up -d";

  @Test
  void healthEndpointIsUp() {
    Response response;
    try {
      response = RestAssured.given().baseUri(QaConfig.ledgerUrl()).get("/actuator/health");
    } catch (RuntimeException e) {
      throw new AssertionError(DOWN, e);
    }
    assertThat(response.statusCode()).as(DOWN).isEqualTo(200);
    assertThat(response.jsonPath().getString("status")).as(DOWN).isEqualTo("UP");
  }
}
