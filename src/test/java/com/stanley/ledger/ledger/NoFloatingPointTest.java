package com.stanley.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Money is a long in minor units. This test fails if float or double appear in the ledger package.
 */
class NoFloatingPointTest {
  private static final Pattern FP = Pattern.compile("\\b(double|float|Double|Float|BigDecimal)\\b");

  @Test
  void ledgerPackageHasNoFloatingPoint() throws IOException {
    Path dir = Path.of("src/main/java/com/stanley/ledger/ledger");
    try (Stream<Path> files = Files.list(dir)) {
      for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        // strip comments before matching, so prose can say "float"
        String code = Files.readString(f).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "");
        assertThat(FP.matcher(code).find()).as(f.toString()).isFalse();
      }
    }
  }
}
