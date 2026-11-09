package com.stanley.ledger.qa.ui.pages;

import com.stanley.ledger.qa.config.QaConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Page object for the Operations console page at #/operations. */
public final class OperationsPage {
    private final RemoteWebDriver driver;
    private final WebDriverWait wait;
    private final By adminKeyInput = By.id("admin-key");
    private final By maxInput = By.id("redrive-max");
    private final By redriveButton = By.xpath("//button[normalize-space()='Redrive']");
    private final By resultStatus =
            By.xpath("//p[@role='status'][starts-with(normalize-space(),'Read ')]");
    private final By dialog =
            By.xpath(
                    "//dialog[.//h2[normalize-space()='Redrive dead-letter topic?']]");
    private final By cancelButton =
            By.xpath(
                    "//dialog[.//h2[normalize-space()='Redrive dead-letter topic?']]"
                            + "//button[normalize-space()='Cancel']");

    public OperationsPage(RemoteWebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public void open() {
        wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//a[normalize-space()='Operations']")))
                .click();
        waitForRedriveForm();
    }

    public void waitForRedriveForm() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(adminKeyInput));
    }

    public void openRedriveDialog(String adminKey) {
        WebElement admin =
                wait.until(ExpectedConditions.visibilityOfElementLocated(adminKeyInput));
        admin.clear();
        admin.sendKeys(adminKey);

        WebElement max = wait.until(ExpectedConditions.visibilityOfElementLocated(maxInput));
        max.clear();
        max.sendKeys("10");

        wait.until(ExpectedConditions.elementToBeClickable(redriveButton)).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(dialog));
    }

    public void cancelRedrive() {
        wait.until(ExpectedConditions.elementToBeClickable(cancelButton)).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(dialog));
    }

    public boolean isDialogVisible() {
        return driver.findElements(dialog).stream().anyMatch(WebElement::isDisplayed);
    }

    public boolean isResultShown() {
        return !driver.findElements(resultStatus).isEmpty();
    }
}
