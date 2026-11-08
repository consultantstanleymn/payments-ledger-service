package com.stanley.ledger.recon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Optional scheduled run. Off unless ledger.recon.settlement-file is set. */
@Component
public class ReconciliationJob {
  private static final Logger LOG = LoggerFactory.getLogger(ReconciliationJob.class);
  private final ReconciliationService service;
  private final String file;

  public ReconciliationJob(
      ReconciliationService service, @Value("${ledger.recon.settlement-file:}") String file) {
    this.service = service;
    this.file = file;
  }

  @Scheduled(cron = "${ledger.recon.cron:0 0 2 * * *}")
  public void run() throws IOException {
    if (file.isBlank()) {
      return;
    }
    try (var reader = Files.newBufferedReader(Path.of(file))) {
      var r = service.reconcile(reader);
      LOG.info(
          "reconciliation matched={} missing={} mismatched={} unexpected={}",
          r.matched(),
          r.missing(),
          r.mismatched(),
          r.unexpected());
    }
  }
}
