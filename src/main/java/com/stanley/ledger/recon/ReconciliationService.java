package com.stanley.ledger.recon;

import com.stanley.ledger.ledger.LedgerException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compares captured payments in the ledger with a processor settlement CSV. CSV header:
 * payment_id,amount_minor. Money columns are integers in minor units.
 */
@Service
public class ReconciliationService {
  public record Report(
      UUID id, int matched, int missing, int mismatched, int unexpected, String details) {}

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public ReconciliationService(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional
  public Report reconcile(Reader csv) {
    Map<UUID, Long> settlement = parse(csv);
    Map<UUID, Long> ledger = new HashMap<>();
    jdbc.sql("SELECT id, captured_minor FROM payments WHERE status = 'CAPTURED'")
        .query((rs, n) -> ledger.put(rs.getObject("id", UUID.class), rs.getLong("captured_minor")))
        .list();

    int matched = 0;
    List<String> missing = new ArrayList<>();
    List<Map<String, Object>> mismatched = new ArrayList<>();
    for (var e : ledger.entrySet()) {
      Long settled = settlement.get(e.getKey());
      if (settled == null) {
        missing.add(e.getKey().toString());
      } else if (!settled.equals(e.getValue())) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paymentId", e.getKey().toString());
        m.put("ledgerMinor", e.getValue());
        m.put("settlementMinor", settled);
        mismatched.add(m);
      } else {
        matched++;
      }
    }
    List<String> unexpected =
        settlement.keySet().stream()
            .filter(k -> !ledger.containsKey(k))
            .map(UUID::toString)
            .toList();

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("missing", missing);
    details.put("mismatched", mismatched);
    details.put("unexpected", unexpected);
    String detailsJson = json.writeValueAsString(details);
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO recon_reports (id, matched, missing, mismatched, unexpected, details)"
                + " VALUES (:i, :a, :b, :c, :d, :e)")
        .param("i", id)
        .param("a", matched)
        .param("b", missing.size())
        .param("c", mismatched.size())
        .param("d", unexpected.size())
        .param("e", detailsJson)
        .update();
    return new Report(
        id, matched, missing.size(), mismatched.size(), unexpected.size(), detailsJson);
  }

  @Transactional(readOnly = true)
  public Report get(UUID id) {
    return jdbc.sql(
            "SELECT id, matched, missing, mismatched, unexpected, details FROM recon_reports"
                + " WHERE id = :i")
        .param("i", id)
        .query(ReconciliationService::map)
        .optional()
        .orElseThrow(() -> LedgerException.notFound("report " + id));
  }

  @Transactional(readOnly = true)
  public Report latest() {
    return jdbc.sql(
            "SELECT id, matched, missing, mismatched, unexpected, details FROM recon_reports"
                + " ORDER BY created_at DESC LIMIT 1")
        .query(ReconciliationService::map)
        .optional()
        .orElseThrow(() -> LedgerException.notFound("no report yet"));
  }

  private static Report map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Report(
        rs.getObject("id", UUID.class),
        rs.getInt("matched"),
        rs.getInt("missing"),
        rs.getInt("mismatched"),
        rs.getInt("unexpected"),
        rs.getString("details"));
  }

  static Map<UUID, Long> parse(Reader csv) {
    Map<UUID, Long> out = new LinkedHashMap<>();
    try (BufferedReader br = new BufferedReader(csv)) {
      String header = br.readLine();
      if (header == null || !header.trim().equals("payment_id,amount_minor")) {
        throw LedgerException.invalid("settlement header must be payment_id,amount_minor");
      }
      String line;
      int row = 1;
      while ((line = br.readLine()) != null) {
        row++;
        if (line.isBlank()) {
          continue;
        }
        String[] p = line.trim().split(",");
        try {
          if (p.length != 2 || out.put(UUID.fromString(p[0]), Long.parseLong(p[1])) != null) {
            throw new IllegalArgumentException("bad or duplicate row");
          }
        } catch (IllegalArgumentException e) {
          throw LedgerException.invalid("settlement row " + row + " is not valid");
        }
      }
    } catch (IOException e) {
      throw LedgerException.invalid("cannot read settlement file");
    }
    return out;
  }
}
