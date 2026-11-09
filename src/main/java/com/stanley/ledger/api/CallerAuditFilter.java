package com.stanley.ledger.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Writes one append-only row per API call. It wraps the API key filter, so calls that the key
 * filter rejects are recorded too, with client id "anonymous". The row holds the client name and
 * never the key. A failed write is logged and counted. It does not fail the request (ADR 0002).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CallerAuditFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(CallerAuditFilter.class);
  private final JdbcClient jdbc;
  private final Counter writeFailures;

  public CallerAuditFilter(JdbcClient jdbc, MeterRegistry registry) {
    this.jdbc = jdbc;
    this.writeFailures = Counter.builder("ledger.caller_audit.write_failures").register(registry);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String p = request.getRequestURI();
    return p.startsWith("/actuator") || p.equals("/openapi.yaml");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(req, res);
    } finally {
      record(req, res);
    }
  }

  private void record(HttpServletRequest req, HttpServletResponse res) {
    try {
      Object client = req.getAttribute(ApiKeyFilter.CLIENT_ATTR);
      Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      int status = res.getStatus();
      String outcome =
          "true".equals(res.getHeader("Idempotent-Replay"))
              ? "replay"
              : status < 400 ? "ok" : status < 500 ? "rejected" : "error";
      var ctx = Span.current().getSpanContext();
      jdbc.sql(
              "INSERT INTO caller_audit (client_id, method, route, idempotency_key, status,"
                  + " outcome, trace_id) VALUES (:c, :m, :r, :k, :s, :o, :t)")
          .param("c", client == null ? "anonymous" : client.toString())
          .param("m", req.getMethod())
          .param("r", pattern == null ? "unmatched" : pattern.toString())
          .param("k", req.getHeader("Idempotency-Key"))
          .param("s", status)
          .param("o", outcome)
          .param("t", ctx.isValid() ? ctx.getTraceId() : null)
          .update();
    } catch (RuntimeException e) {
      writeFailures.increment();
      LOG.warn("caller audit write failed: {}", e.toString());
    }
  }
}
