package com.stanley.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The spec must list every route that the controller maps. Catches drift without a server. */
class OpenApiSpecTest {
  @Test
  void specListsEveryControllerRoute() throws IOException {
    String controller =
        Files.readString(Path.of("src/main/java/com/stanley/ledger/api/LedgerController.java"));
    String spec = Files.readString(Path.of("src/main/resources/static/openapi.yaml"));
    Set<String> routes = new TreeSet<>();
    Matcher m =
        Pattern.compile("@(?:Get|Post)Mapping\\((?:path = )?\"([^\"]+)\"").matcher(controller);
    while (m.find()) routes.add(m.group(1));
    assertThat(routes).hasSizeGreaterThanOrEqualTo(10);
    for (String r : routes) {
      assertThat(spec).as("route " + r).contains("\n  " + r + ":\n");
    }
  }
}
