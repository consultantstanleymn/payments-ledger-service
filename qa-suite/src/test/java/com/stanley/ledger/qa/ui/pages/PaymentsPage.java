package com.stanley.ledger.qa.ui.pages;

import com.stanley.ledger.qa.config.QaConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Page object for the Payments console page at #/payments. */
public final class PaymentsPage {
    private final RemoteWebDriver driver;
    private final WebDriverWait wait;
    private final By referenceInput = By.id("payment-reference");
    private final By lookupButton = By.xpath("//button[normalize-space()='Look up']");

    public PaymentsPage(RemoteWebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public void open() {
        wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//a[normalize-space()='Payments']")))
                .click();
        waitForReferenceVisible();
    }

    public void waitForReferenceVisible() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(referenceInput));
    }

    public void lookup(String externalRef) {
        WebElement input =
                wait.until(ExpectedConditions.visibilityOfElementLocated(referenceInput));
        input.clear();
        input.sendKeys(externalRef);
        wait.until(ExpectedConditions.elementToBeClickable(lookupButton)).click();
    }

    public String capturedAmount(String externalRef) {
        WebElement detail =
                wait.until(
                        ExpectedConditions.visibilityOfElementLocated(detailPanel(externalRef)));
        return detail.findElement(By.xpath(".//div[dt[normalize-space()='Captured']]/dd"))
                .getText();
    }

    public boolean isDetailVisible(String externalRef) {
        return !driver.findElements(detailPanel(externalRef)).isEmpty();
    }

    private By detailPanel(String externalRef) {
        return By.xpath("//aside[@aria-label='Payment " + externalRef + " details']");
    }
}
