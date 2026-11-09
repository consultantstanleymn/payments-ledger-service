package com.stanley.ledger.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ConsoleHeadersFilter extends OncePerRequestFilter {

  static final String CSP =
      "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
          + "connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String p = request.getRequestURI();
    return !(p.equals("/console") || p.startsWith("/console/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    res.setHeader("Content-Security-Policy", CSP);
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "no-referrer");
    chain.doFilter(req, res);
  }
}
