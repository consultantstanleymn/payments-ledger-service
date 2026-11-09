package com.stanley.ledger.qa.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestRunFinished;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs every feature under the features resource folder on the Cucumber engine. Two small plugins
 * write the HTML and JSON reports. They read the Cucumber event stream directly, so the suite does
 * not need a JSON library at test scope.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.stanley.ledger.qa.bdd.steps")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value =
        "com.stanley.ledger.qa.bdd.RunCucumberTest$HtmlReport:target/cucumber-report.html,"
            + "com.stanley.ledger.qa.bdd.RunCucumberTest$JsonReport:target/cucumber.json")
public class RunCucumberTest {

  private record Scenario(String uri, String name, String status, long durationMillis) {}

  private abstract static class ScenarioCollector implements EventListener {
    private final List<Scenario> scenarios = new ArrayList<>();

    @Override
    public final void setEventPublisher(EventPublisher publisher) {
      publisher.registerHandlerFor(TestCaseFinished.class, this::finished);
      publisher.registerHandlerFor(TestRunFinished.class, event -> write(scenarios));
    }

    private void finished(TestCaseFinished event) {
      Result result = event.getResult();
      scenarios.add(
          new Scenario(
              event.getTestCase().getUri().toString(),
              event.getTestCase().getName(),
              result.getStatus().name(),
              result.getDuration().toMillis()));
    }

    protected abstract void write(List<Scenario> scenarios);
  }

  /** Writes a compact HTML table of every scenario result. */
  public static final class HtmlReport extends ScenarioCollector {
    private final PrintStream out;

    public HtmlReport(OutputStream stream) {
      this.out = new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    @Override
    protected void write(List<Scenario> scenarios) {
      long passed = scenarios.stream().filter(s -> "PASSED".equals(s.status())).count();
      out.println("<!DOCTYPE html>");
      out.println("<html lang=\"en\"><head><meta charset=\"utf-8\">");
      out.println("<title>Cucumber report</title>");
      out.println(
          "<style>body{font-family:sans-serif;margin:2rem}"
              + "table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:.3rem .6rem}"
              + ".PASSED{color:#1a7f37}.FAILED{color:#b42318}</style>");
      out.println("</head><body>");
      out.println("<h1>Cucumber report</h1>");
      out.printf("<p>%d scenarios, %d passed</p>%n", scenarios.size(), passed);
      out.println("<table><thead><tr><th>Feature</th><th>Scenario</th><th>Status</th>"
          + "<th>Duration (ms)</th></tr></thead><tbody>");
      for (Scenario scenario : scenarios) {
        out.printf(
            "<tr><td>%s</td><td>%s</td><td class=\"%s\">%s</td><td>%d</td></tr>%n",
            escape(scenario.uri()),
            escape(scenario.name()),
            escape(scenario.status()),
            escape(scenario.status()),
            scenario.durationMillis());
      }
      out.println("</tbody></table>");
      out.println("</body></html>");
      out.flush();
    }
  }

  /** Writes one JSON object per scenario, as a JSON array. */
  public static final class JsonReport extends ScenarioCollector {
    private final PrintStream out;

    public JsonReport(OutputStream stream) {
      this.out = new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    @Override
    protected void write(List<Scenario> scenarios) {
      out.println("[");
      for (int i = 0; i < scenarios.size(); i++) {
        Scenario scenario = scenarios.get(i);
        String comma = i + 1 < scenarios.size() ? "," : "";
        out.printf(
            "  {\"uri\":\"%s\",\"name\":\"%s\",\"status\":\"%s\",\"durationMillis\":%d}%s%n",
            escapeJson(scenario.uri()),
            escapeJson(scenario.name()),
            escapeJson(scenario.status()),
            scenario.durationMillis(),
            comma);
      }
      out.println("]");
      out.flush();
    }
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
