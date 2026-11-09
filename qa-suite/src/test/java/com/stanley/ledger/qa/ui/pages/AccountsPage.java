package com.stanley.ledger.qa.ui.pages;

import com.stanley.ledger.qa.config.QaConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Page object for the Accounts console page at #/accounts. */
public final class AccountsPage {
    private final RemoteWebDriver driver;
    private final WebDriverWait wait;
    private final By searchInput = By.id("account-search");

    public AccountsPage(RemoteWebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public void open() {
        driver.get(QaConfig.consoleUrl() + "#/accounts");
        waitForSearchVisible();
    }

    public void waitForSearchVisible() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(searchInput));
    }

    public boolean isSearchVisible() {
        return !driver.findElements(searchInput).isEmpty();
    }

    public void search(String query) {
        WebElement input =
                wait.until(ExpectedConditions.visibilityOfElementLocated(searchInput));
        input.clear();
        input.sendKeys(query);
    }

    public void waitForAccountButton(String accountName) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(accountButton(accountName)));
    }

    public boolean isAccountButtonVisible(String accountName) {
        return !driver.findElements(accountButton(accountName)).isEmpty();
    }

    public void openAccount(String accountName) {
        wait.until(ExpectedConditions.elementToBeClickable(accountButton(accountName))).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(drawerByName(accountName)));
    }

    public String drawerAvailableText(String accountName) {
        WebElement drawer =
                wait.until(ExpectedConditions.visibilityOfElementLocated(drawerByName(accountName)));
        return drawer.findElement(
                        By.xpath(".//p[starts-with(normalize-space(),'Available:')]"))
                .getText();
    }

    private By accountButton(String accountName) {
        return By.xpath("//button[normalize-space()='" + accountName + "']");
    }

    private By drawerByName(String accountName) {
        return By.xpath(
                "//div[@role='dialog'][.//h2[normalize-space()='"
                        + accountName
                        + " entries']]");
    }
}
