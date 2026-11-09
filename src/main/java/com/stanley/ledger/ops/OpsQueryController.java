package com.stanley.ledger.ops;

import com.stanley.ledger.ops.OpsQueryService.AccountView;
import com.stanley.ledger.ops.OpsQueryService.EntryView;
import com.stanley.ledger.ops.OpsQueryService.PaymentView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only routes for the operations console. No SQL here, only calls to OpsQueryService. */
@RestController
public class OpsQueryController {
  private final OpsQueryService service;

  public OpsQueryController(OpsQueryService service) {
    this.service = service;
  }

  @GetMapping("/ops/accounts")
  public PageResult<AccountView> accounts(
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "25") int limit,
      @RequestParam(required = false) String cursor) {
    return service.accounts(currency, q, limit, cursor);
  }

  @GetMapping("/ops/accounts/{id}/entries")
  public PageResult<EntryView> entries(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "25") int limit,
      @RequestParam(required = false) String cursor) {
    return service.entries(id, limit, cursor);
  }

  @GetMapping("/ops/payments")
  public PageResult<PaymentView> payments(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "25") int limit,
      @RequestParam(required = false) String cursor) {
    return service.payments(status, limit, cursor);
  }
}
