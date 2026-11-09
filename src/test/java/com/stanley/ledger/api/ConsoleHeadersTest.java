package com.stanley.ledger.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stanley.ledger.LedgerProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ConsoleHeadersTest {

  MockMvc mvc;

  @BeforeEach
  void setUp() {
    LedgerProperties props = mock(LedgerProperties.class);
    when(props.apiKeys()).thenReturn(new java.util.HashMap<>(Map.of("dev-key-local-only", "dev")));
    mvc =
        MockMvcBuilders.standaloneSetup(new StubController())
            .addFilters(new ConsoleHeadersFilter(), new ApiKeyFilter(props))
            .build();
  }

  @Test
  void consoleNeedsNoApiKey() throws Exception {
    mvc.perform(get("/console/index.html")).andExpect(status().isOk());
  }

  @Test
  void consoleHasCsp() throws Exception {
    mvc.perform(get("/console/index.html"))
        .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"));
  }

  @Test
  void apiRoutesStillNeedKey() throws Exception {
    mvc.perform(get("/ops/accounts")).andExpect(status().isUnauthorized());
  }

  @RestController
  static class StubController {
    @GetMapping("/console/index.html")
    String console() {
      return "console ok";
    }

    @GetMapping("/ops/accounts")
    Map<String, Object> accounts() {
      return Map.of("items", List.of());
    }
  }
}
