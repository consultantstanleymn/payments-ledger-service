package com.stanley.ledger.ops;

import com.stanley.ledger.ledger.LedgerException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Opaque keyset paging cursor. The wire form is unpadded base64url of a small JSON object. */
public record Cursor(String sort, String key, String id) {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  public static String encode(String sort, String key, String id) {
    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("k", key);
    payload.put("id", id);
    payload.put("s", sort);
    byte[] bytes = JSON.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static Cursor decode(String encoded, String expectedSort) {
    if (encoded == null || encoded.isBlank()) {
      throw bad("cursor is empty");
    }
    try {
      byte[] bytes = Base64.getUrlDecoder().decode(encoded);
      JsonNode node = JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
      if (node == null || !node.isObject()) {
        throw bad("cursor payload is not an object");
      }
      String sort = text(node, "s");
      String key = text(node, "k");
      String id = text(node, "id");
      if (!expectedSort.equals(sort)) {
        throw bad("cursor belongs to another sort");
      }
      return new Cursor(sort, key, id);
    } catch (LedgerException e) {
      throw e;
    } catch (RuntimeException e) {
      throw bad("cursor is not valid");
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString() || value.asString().isBlank()) {
      throw bad("cursor field " + field + " is missing");
    }
    return value.asString();
  }

  private static LedgerException bad(String message) {
    return new LedgerException(400, "INVALID_CURSOR", message);
  }
}
