package com.stanley.ledger.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.config.QaConfig;
import com.stanley.ledger.qa.support.LedgerDb;
import com.stanley.ledger.qa.support.RunId;
import com.stanley.ledger.qa.support.TestData;
import com.stanley.ledger.qa.ui.pages.AccountsPage;
import com.stanley.ledger.qa.ui.pages.OperationsPage;
import com.stanley.ledger.qa.ui.pages.PaymentsPage;
import com.stanley.ledger.qa.ui.pages.SignInPage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ui")
class ConsoleUiTest extends UiTestBase {

    @Test
    void signInWithBadKeyShowsAlert() {
        SignInPage signInPage = new SignInPage(driver, wait);
        signInPage.signIn("bad-key-local-only");

        assertThat(signInPage.alertText()).contains("Invalid API key.");
    }

    @Test
    void signInLandsOnAccounts() {
        SignInPage signInPage = new SignInPage(driver, wait);
        signInPage.signIn(QaConfig.apiKey());

        AccountsPage accountsPage = new AccountsPage(driver, wait);
        accountsPage.waitForSearchVisible();
        assertThat(accountsPage.isSearchVisible()).isTrue();
    }

    @Test
    void accountsShowsRunAccountsAfterSearch() {
        TestData testData = new TestData();
        TestData.Account first = testData.account().create();
        TestData.Account second = testData.account().create();

        new SignInPage(driver, wait).signIn(QaConfig.apiKey());

        AccountsPage accountsPage = new AccountsPage(driver, wait);
        accountsPage.waitForSearchVisible();
        accountsPage.search("qa-" + RunId.value());
        accountsPage.waitForAccountButton(first.name());
        accountsPage.waitForAccountButton(second.name());

        assertThat(accountsPage.isAccountButtonVisible(first.name())).isTrue();
        assertThat(accountsPage.isAccountButtonVisible(second.name())).isTrue();
    }

    @Test
    void entriesDrawerShowsBalancedTransaction() {
        TestData testData = new TestData();
        LedgerDb ledgerDb = new LedgerDb();
        long fundedMinor = 50000L;

        TestData.Account bank =
                testData.account().currency("USD").floorMinor(-fundedMinor).create();
        TestData.Account payer = testData.account().currency("USD").floorMinor(0).create();
        TestData.Transfer transfer =
                testData.transfer().from(bank).to(payer).amount(fundedMinor).post();

        assertThat(ledgerDb.journalBalancedFor(List.of(transfer.transactionId()))).isTrue();

        new SignInPage(driver, wait).signIn(QaConfig.apiKey());

        AccountsPage accountsPage = new AccountsPage(driver, wait);
        accountsPage.waitForSearchVisible();
        accountsPage.search(payer.name());
        accountsPage.openAccount(payer.name());

        assertThat(accountsPage.drawerAvailableText(payer.name()))
                .contains("Available: " + formatMinor(fundedMinor, "USD"));
    }

    @Test
    void paymentLookupShowsCapturedAmount() {
        TestData testData = new TestData();
        long amountMinor = 45250L;
        String externalRef = "qa-" + UUID.randomUUID();

        TestData.Account bank =
                testData.account().currency("USD").floorMinor(-amountMinor).create();
        TestData.Account payer = testData.account().currency("USD").floorMinor(0).create();
        TestData.Account payee = testData.account().currency("USD").floorMinor(0).create();

        testData.transfer().from(bank).to(payer).amount(amountMinor).post();
        TestData.Payment payment =
                testData
                        .payment()
                        .from(payer)
                        .to(payee)
                        .amount(amountMinor)
                        .externalRef(externalRef)
                        .authorize();
        testData.capture(payment, amountMinor);

        new SignInPage(driver, wait).signIn(QaConfig.apiKey());

        PaymentsPage paymentsPage = new PaymentsPage(driver, wait);
        paymentsPage.open();
        paymentsPage.lookup(externalRef);

        assertThat(paymentsPage.capturedAmount(externalRef))
                .isEqualTo(formatMinor(amountMinor, "USD").replace(" USD", ""));
    }

    @Test
    void redriveDialogCancelSendsNothing() {
        new SignInPage(driver, wait).signIn(QaConfig.apiKey());

        OperationsPage operationsPage = new OperationsPage(driver, wait);
        operationsPage.open();
        operationsPage.openRedriveDialog("qa-admin-key");

        assertThat(operationsPage.isDialogVisible()).isTrue();

        operationsPage.cancelRedrive();

        assertThat(operationsPage.isDialogVisible()).isFalse();
        assertThat(operationsPage.isResultShown()).isFalse();
    }
}
