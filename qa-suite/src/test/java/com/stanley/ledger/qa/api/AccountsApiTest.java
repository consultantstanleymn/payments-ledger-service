package com.stanley.ledger.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.support.ApiClient;
import com.stanley.ledger.qa.support.RunId;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Positive account routes seen from the outside. */
class AccountsApiTest {
  private final ApiClient api = new ApiClient();

  @Test
  void createAccountReturns201() {
    String body =
        String.format(
            "{\"name\":\"%s\",\"currency\":\"USD\",\"floorMinor\":-1000}", RunId.accountName(1));
    Response created = api.withIdempotency(api.newIdempotencyKey()).body(body).post("/accounts");

    assertThat(created.statusCode()).isEqualTo(201);
    JsonPath json = created.jsonPath();
    assertThat(json.getString("accountId")).isNotBlank();
    assertThat(json.getString("currency")).isEqualTo("USD");
    assertThat(json.getLong("floorMinor")).isEqualTo(-1000L);
    assertThat(json.getLong("postedMinor")).isZero();
    assertThat(json.getLong("heldMinor")).isZero();
    assertThat(json.getLong("availableMinor")).isZero();

    UUID id = UUID.fromString(json.getString("accountId"));
    Response balance = api.request().get("/accounts/{id}/balance", id.toString());

    assertThat(balance.statusCode()).isEqualTo(200);
    assertThat(balance.jsonPath().getString("accountId")).isEqualTo(id.toString());
    assertThat(balance.jsonPath().getString("currency")).isEqualTo("USD");
    assertThat(balance.jsonPath().getLong("floorMinor")).isEqualTo(-1000L);
    assertThat(balance.jsonPath().getLong("availableMinor")).isZero();
  }
}
