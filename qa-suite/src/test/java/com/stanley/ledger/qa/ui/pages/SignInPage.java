package com.stanley.ledger.qa.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Page object for the console sign-in screen at #/signin. */
public final class SignInPage {
    private final WebDriverWait wait;
    private final By apiKeyInput = By.id("api-key");
    private final By signInButton = By.xpath("//button[normalize-space()='Sign in']");
    private final By alert = By.cssSelector("[role='alert']");

    public SignInPage(RemoteWebDriver driver, WebDriverWait wait) {
        this.wait = wait;
    }

    public void signIn(String apiKey) {
        fillApiKey(apiKey);
        submit();
    }

    public void fillApiKey(String apiKey) {
        WebElement input =
                wait.until(ExpectedConditions.visibilityOfElementLocated(apiKeyInput));
        input.sendKeys(apiKey);
    }

    public void submit() {
        wait.until(ExpectedConditions.elementToBeClickable(signInButton)).click();
    }

    public String alertText() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(alert)).getText();
    }
}
