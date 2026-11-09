package com.stanley.ledger;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * apiKeys maps a static API key to a client id. Placeholder for OAuth2, see SECURITY.md. adminKey
 * guards the admin routes. When it is empty, the admin routes answer 403.
 */
@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(Map<String, String> apiKeys, String adminKey) {}
