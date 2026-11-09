package com.stanley.ledger.qa.ui;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.stanley.ledger.qa.config.QaConfig;
import java.io.File;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Shared Selenium setup. Tests extend this class and use the active driver and wait. */
public abstract class UiTestBase {
    protected RemoteWebDriver driver;
    protected WebDriverWait wait;

    @BeforeEach
    void setUp() throws Exception {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                "--window-size=1366,900");
        driver = new RemoteWebDriver(new URL(QaConfig.seleniumUrl()), options);
        driver.manage().window().setSize(new Dimension(1366, 900));
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        driver.get(QaConfig.consoleUrl() + "#/signin");
        ((JavascriptExecutor) driver)
                .executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
        driver.get(QaConfig.consoleUrl() + "#/signin");
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        if (driver == null) {
            return;
        }

        String testName =
                testInfo.getTestMethod().map(Method::getName).orElse("ui-test");
        try {
            captureScreenshot(testName);
        } finally {
            try {
                driver.quit();
            } catch (Exception ignored) {
                // The browser may already be closed by a failed session.
            }
        }
    }

    protected static String formatMinor(long amountMinor, String currency) {
        BigInteger amount = BigInteger.valueOf(amountMinor);
        boolean negative = amount.signum() < 0;
        BigInteger unsigned = amount.abs();
        BigInteger[] parts = unsigned.divideAndRemainder(BigInteger.valueOf(100));
        String major = String.format("%,d", parts[0]);
        String minor = String.format("%02d", parts[1].intValue());
        return (negative ? "-" : "") + major + "." + minor + " " + currency;
    }

    private void captureScreenshot(String testName) {
        try {
            Path base = screenshotBase();
            Files.createDirectories(base);
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(
                    screenshot.toPath(),
                    base.resolve(testName + ".png"),
                    REPLACE_EXISTING);
        } catch (Exception ignored) {
            // Screenshots are diagnostics; never fail a test because one could not be written.
        }
    }

    private static Path screenshotBase() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        if (Files.isRegularFile(cwd.resolve("qa-suite/pom.xml"))) {
            return cwd.resolve("qa-suite/target/ui-screens");
        }
        return cwd.resolve("target/ui-screens");
    }
}
