package com.stanley.ledger.api;

import com.stanley.ledger.LedgerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Static API key check. Actuator and the OpenAPI file are open. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {
  static final String CLIENT_ATTR = "ledger.clientId";
  private final LedgerProperties props;

  public ApiKeyFilter(LedgerProperties props) {
    this.props = props;
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
    String client =
        props.apiKeys() == null ? null : props.apiKeys().get(req.getHeader("X-Api-Key"));
    if (client == null) {
      res.setStatus(401);
      res.setContentType("application/json");
      res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"missing or bad X-Api-Key\"}");
      return;
    }
    req.setAttribute(CLIENT_ATTR, client);
    chain.doFilter(req, res);
  }
}
