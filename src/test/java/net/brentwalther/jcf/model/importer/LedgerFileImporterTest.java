package net.brentwalther.jcf.model.importer;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelTransforms;
import org.junit.Test;

import java.math.BigDecimal;

import static com.google.common.truth.Truth.assertThat;

public class LedgerFileImporterTest {

  private static final Correspondence<Account, String> ACCOUNT_NAME_CORRESPONDENCE =
      Correspondence.transforming(Account::getName, "is an account with name");
  private static final Correspondence<Transaction, String> TRANSACTION_DESCRIPTION_CORRESPONDENCE =
      Correspondence.transforming(Transaction::getDescription, "is a transaction with description");
  private static final Correspondence<BigDecimal, BigDecimal> BIGDECIMAL_COMPARETO_CORRESPONDENCE =
      Correspondence.from(
          (first, second) -> first != null && second != null && first.compareTo(second) == 0,
          "is a big decimal amount that compares equally");

  @Test
  public void testEmptyFile() {
    Model model = LedgerFileImporter.create(ImmutableList.of()).get();
    assertThat(model.getAccountList()).isEmpty();
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testUnrecognizedLine() {
    Model model =
        LedgerFileImporter.create(
                ImmutableList.of("a test", "of random", "lines that", "mean nothing"))
            .get();
    assertThat(model.getAccountList()).isEmpty();
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testValidAccountLine() {
    Model model =
        LedgerFileImporter.create(ImmutableList.of("account Assets:Retirement:401k")).get();
    assertThat(model.getAccountList())
        .comparingElementsUsing(ACCOUNT_NAME_CORRESPONDENCE)
        .containsExactly("Assets:Retirement:401k");
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testAccountDeclarationWithSpacesLine() {
    Model model =
        LedgerFileImporter.create(ImmutableList.of("account Assets:Credit Cards:Chase")).get();
    assertThat(model.getAccountList())
        .comparingElementsUsing(ACCOUNT_NAME_CORRESPONDENCE)
        .containsExactly("Assets:Credit Cards:Chase");
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testBasicCompleteTransaction() {
    Model model =
        LedgerFileImporter.create(
                ImmutableList.of(
                    "2020-10-31 * Halloween superstore",
                    "  Liabilities:Credit Cards:Chase  $-99",
                    "  Expenses:Misc $99"))
            .get();
    assertThat(model.getAccountList())
        .comparingElementsUsing(ACCOUNT_NAME_CORRESPONDENCE)
        .containsExactly("Liabilities:Credit Cards:Chase", "Expenses:Misc");
    assertThat(model.getTransactionList())
        .comparingElementsUsing(TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("Halloween superstore");
    assertThat(
            FluentIterable.from(model.getSplitList())
                .transform(ModelTransforms::bigDecimalAmountForSplit))
        .comparingElementsUsing(BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(new BigDecimal("99"), new BigDecimal("-99"));
  }

  @Test
  public void testTransactionWithSlashDate() {
    Model model =
        LedgerFileImporter.create(
                ImmutableList.of(
                    "2020/10/31 * Halloween superstore",
                    "  Liabilities:Credit Cards:Chase  $-99",
                    "  Expenses:Misc $99"))
            .get();
    assertThat(model.getAccountList())
        .comparingElementsUsing(ACCOUNT_NAME_CORRESPONDENCE)
        .containsExactly("Liabilities:Credit Cards:Chase", "Expenses:Misc");
    assertThat(model.getTransactionList())
        .comparingElementsUsing(TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("Halloween superstore");
    assertThat(
            FluentIterable.from(model.getSplitList())
                .transform(ModelTransforms::bigDecimalAmountForSplit))
        .comparingElementsUsing(BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(new BigDecimal("99"), new BigDecimal("-99"));
  }

  @Test
  public void testTransactionWithoutClearStatus() {
    // The first line of the transaction doesn't have the clear status (asterisk or exclamation
    // point).
    Model model =
        LedgerFileImporter.create(
                ImmutableList.of(
                    "2020-10-31 Halloween superstore",
                    "  Liabilities:Credit Cards:Chase  $-99",
                    "  Expenses:Misc $99"))
            .get();
    assertThat(model.getAccountList())
        .comparingElementsUsing(ACCOUNT_NAME_CORRESPONDENCE)
        .containsExactly("Liabilities:Credit Cards:Chase", "Expenses:Misc");
    assertThat(model.getTransactionList())
        .comparingElementsUsing(TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("Halloween superstore");
    assertThat(
            FluentIterable.from(model.getSplitList())
                .transform(ModelTransforms::bigDecimalAmountForSplit))
        .comparingElementsUsing(BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(new BigDecimal("99"), new BigDecimal("-99"));
  }

  @Test
  public void testTransactionWithImpliedBalance() {
    // The last split doesn't have an amount which implies it takes the remaining balance.
    Model model =
        LedgerFileImporter.create(
                ImmutableList.of(
                    "2020-10-31 * Halloween superstore",
                    "  Liabilities:Credit Cards:Chase  $-99",
                    "  Expenses:Misc"))
            .get();
    assertThat(model.getAccountList())
        .comparingElementsUsing(ACCOUNT_NAME_CORRESPONDENCE)
        .containsExactly("Liabilities:Credit Cards:Chase", "Expenses:Misc");
    assertThat(model.getTransactionList())
        .comparingElementsUsing(TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("Halloween superstore");
    assertThat(
            FluentIterable.from(model.getSplitList())
                .transform(ModelTransforms::bigDecimalAmountForSplit))
        .comparingElementsUsing(BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(new BigDecimal("99"), new BigDecimal("-99"));
  }

  @Test
  public void testTransactionWithMultipleSplits() {
    Model model =
        LedgerFileImporter.create(
                ImmutableList.of(
                    "2020-10-31 * Investment BUY",
                    "  Assets:Bank:Checking  $-1000",
                    "  Assets:Investments:VTSAX  $600",
                    "  Assets:Investments:VTIAX  $250",
                    "  Assets:Investments:VBTLX  $150"))
            .get();
    assertThat(model.getAccountList()).hasSize(4);
    assertThat(model.getTransactionList())
        .comparingElementsUsing(TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("Investment BUY");
    assertThat(
            FluentIterable.from(model.getSplitList())
                .transform(ModelTransforms::bigDecimalAmountForSplit))
        .comparingElementsUsing(BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(
            new BigDecimal("-1000"),
            new BigDecimal("600"),
            new BigDecimal("250"),
            new BigDecimal("150"));
  }

  @Test
  public void testAccountsAreProperlyReused() {
    Model model =
        LedgerFileImporter.create(
                ImmutableList.of(
                    "2020-10-31 Halloween superstore",
                    "  Liabilities:Credit Cards:Chase  $-99",
                    "  Expenses:Misc $99",
                    "",
                    "2020-10-31 Chuys Mexican Food",
                    "  Liabilities:Credit Cards:Chase  $-75",
                    "  Expenses:Restaurants $75",
                    "",
                    "2020-10-31 Shell",
                    "  Liabilities:Credit Cards:Chase  $-50",
                    "  Expenses:Auto:Gas $50"))
            .get();
    assertThat(model.getAccountList())
        .comparingElementsUsing(ACCOUNT_NAME_CORRESPONDENCE)
        .containsExactly(
            "Liabilities:Credit Cards:Chase",
            "Expenses:Misc",
            "Expenses:Restaurants",
            "Expenses:Auto:Gas");
    assertThat(model.getTransactionList()).hasSize(3);
    assertThat(
            FluentIterable.from(model.getSplitList())
                .transform(ModelTransforms::bigDecimalAmountForSplit))
        .comparingElementsUsing(BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsAtLeast(new BigDecimal("99"), new BigDecimal("75"), new BigDecimal("50"));
  }
}
