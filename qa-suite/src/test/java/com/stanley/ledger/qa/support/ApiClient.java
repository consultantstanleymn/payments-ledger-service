package com.stanley.ledger.qa.support;

import com.stanley.ledger.qa.config.QaConfig;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.UUID;

/** REST Assured entry point. It sets the base URI, the API key and the JSON content type. */
public final class ApiClient {
  private final RequestSpecification spec;

  public ApiClient() {
    this.spec =
        new RequestSpecBuilder()
            .setBaseUri(QaConfig.ledgerUrl())
            .setContentType(ContentType.JSON)
            .addHeader("X-Api-Key", QaConfig.apiKey())
            .setConfig(
                RestAssuredConfig.config()
                    .logConfig(
                        LogConfig.logConfig().enableLoggingOfRequestAndResponseIfValidationFails()))
            .build();
  }

  /** A fresh request with the shared settings. */
  public RequestSpecification request() {
    return io.restassured.RestAssured.given().spec(spec);
  }

  /** A request that carries an Idempotency-Key header. */
  public RequestSpecification withIdempotency(String key) {
    return request().header("Idempotency-Key", key);
  }

  public String newIdempotencyKey() {
    return UUID.randomUUID().toString();
  }
}
