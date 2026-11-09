package com.stanley.ledger.api;

import com.stanley.ledger.LedgerProperties;
import com.stanley.ledger.projection.DltRedriver;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin routes. They need X-Admin-Key. With no admin key configured, they answer 403. */
@RestController
public class AdminController {
  private final LedgerProperties props;
  private final DltRedriver redriver;

  public AdminController(LedgerProperties props, DltRedriver redriver) {
    this.props = props;
    this.redriver = redriver;
  }

  @PostMapping("/admin/dlt/redrive")
  public ResponseEntity<Map<String, Object>> redrive(
      @RequestParam(defaultValue = "100") int max,
      @RequestHeader(value = "X-Admin-Key", required = false) String key) {
    String expected = props.adminKey();
    if (expected == null || expected.isBlank() || !expected.equals(key)) {
      return ResponseEntity.status(403)
          .body(Map.of("code", "FORBIDDEN", "message", "missing or bad X-Admin-Key"));
    }
    int bounded = Math.min(Math.max(max, 1), 1000);
    var result = redriver.redrive(bounded);
    return ResponseEntity.ok(Map.of("read", result.read(), "republished", result.republished()));
  }
}
