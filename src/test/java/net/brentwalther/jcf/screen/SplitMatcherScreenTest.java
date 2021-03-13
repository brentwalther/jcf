package net.brentwalther.jcf.screen;

import static com.google.common.truth.Truth.assertThat;
import static net.brentwalther.jcf.model.ModelGenerators.simpleAccount;
import static net.brentwalther.jcf.testing.ArgumentMatchers.IS_A_DECORATED_OPTIONS_PROMPT;
import static net.brentwalther.jcf.testing.Correspondences.SPLIT_WITH_ACCOUNT_ID_CORRESPONDENCE;
import static net.brentwalther.jcf.testing.Correspondences.SPLIT_WITH_BIGDECIMAL_AMOUNT_COMPARETO_CORRESPONDENCE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.prompt.AccountPickerPrompt;
import net.brentwalther.jcf.prompt.BigDecimalPrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.Prompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.screen.SplitMatcherScreen.Option;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class SplitMatcherScreenTest {

  private static final BigDecimal DEFAULT_SPLIT_AMOUNT = new BigDecimal("100");
  private static final Account IMPORT_ACCOUNT = simpleAccount("Liabilities:Chase 1234");
  private static final ImmutableList<Transaction> TEST_TRANSACTIONS =
      ImmutableList.of(
          Transaction.newBuilder()
              .setId("test-id")
              .setDescription("Shell gasoline")
              .setPostDateEpochSecond(Instant.now().getEpochSecond())
              .build(),
          Transaction.newBuilder()
              .setId("test-id2")
              .setDescription("Power company e-bill")
              .setPostDateEpochSecond(Instant.now().getEpochSecond())
              .build(),
          Transaction.newBuilder()
              .setId("test-id3")
              .setDescription("Ticketmaster sux")
              .setPostDateEpochSecond(Instant.now().getEpochSecond())
              .build(),
          Transaction.newBuilder()
              .setId("test-id4")
              .setDescription("Smallest Bar in Texas")
              .setPostDateEpochSecond(Instant.now().getEpochSecond())
              .build(),
          Transaction.newBuilder()
              .setId("test-id5")
              .setDescription("OnlineRent")
              .setPostDateEpochSecond(Instant.now().getEpochSecond())
              .build());
  private static final ImmutableList<Account> EXPENSE_ACCOUNTS =
      ImmutableList.of(
          simpleAccount("Expenses:Auto"),
          simpleAccount("Expenses:Bills"),
          simpleAccount("Expenses:Entertainment"),
          simpleAccount("Expenses:Food/Drink"),
          simpleAccount("Expenses:Rent"));
  private static final ImmutableList<Account> ALL_ACCOUNTS =
      ImmutableList.<Account>builder().add(IMPORT_ACCOUNT).addAll(EXPENSE_ACCOUNTS).build();

  private PromptEvaluator mockPromptEvaluator;
  private SplitMatcher mockSplitMatcher;

  @Before
  public void setUp() {
    mockPromptEvaluator = mock(PromptEvaluator.class);
    mockSplitMatcher = mock(SplitMatcher.class);
  }

  @Test
  public void testBoringMocksDontCrashIt() {
    Model actualOutputModel =
        SplitMatcherScreen.start(
            mockPromptEvaluator,
            mockSplitMatcher,
            /* modelToMatch= */ IndexedModel.create(Model.getDefaultInstance()),
            /* initiallyKnownAccountsById= */ ImmutableMap.of());

    verify(mockPromptEvaluator, never()).blockingGetResult(any());
    assertThat(actualOutputModel.getAccountList()).isEmpty();
    assertThat(actualOutputModel.getTransactionList()).isEmpty();
    assertThat(actualOutputModel.getSplitList()).isEmpty();
  }

  @Test
  public void testAllMatchToSingleAccount() {
    ImmutableMap<Class<?>, Supplier<Result<?>>> resultByPromptClass =
        ImmutableMap.of(
            OptionsPrompt.class, () -> Result.string(EXPENSE_ACCOUNTS.get(0).getName()));
    when(mockPromptEvaluator.blockingGetResult(any()))
        .thenAnswer(usingResultCalculatorByPromptClass(resultByPromptClass));
    when(mockSplitMatcher.getTopMatches(any(), any(), any())).thenReturn(ImmutableList.of());
    Model actualOutputModel =
        SplitMatcherScreen.start(
            mockPromptEvaluator,
            mockSplitMatcher,
            /* modelToMatch= */ importedModel(TEST_TRANSACTIONS),
            /* initiallyKnownAccountsById= */ Maps.uniqueIndex(EXPENSE_ACCOUNTS, Account::getId));

    // Verify the prompt evaluator was invoked exactly twice. Once for each transaction to match.
    verify(mockPromptEvaluator, times(EXPENSE_ACCOUNTS.size()))
        .blockingGetResult(argThat(IS_A_DECORATED_OPTIONS_PROMPT));
    assertThat(actualOutputModel.getAccountList()).containsExactlyElementsIn(ALL_ACCOUNTS);
    assertThat(actualOutputModel.getTransactionList()).containsExactlyElementsIn(TEST_TRANSACTIONS);
    assertThat(actualOutputModel.getSplitList())
        .comparingElementsUsing(SPLIT_WITH_BIGDECIMAL_AMOUNT_COMPARETO_CORRESPONDENCE)
        .containsAnyIn(ImmutableSet.of(DEFAULT_SPLIT_AMOUNT, DEFAULT_SPLIT_AMOUNT.negate()));
    assertThat(actualOutputModel.getSplitList())
        .comparingElementsUsing(SPLIT_WITH_ACCOUNT_ID_CORRESPONDENCE)
        .containsAnyOf(EXPENSE_ACCOUNTS.get(0).getId(), IMPORT_ACCOUNT.getId());
  }

  @Test
  public void testCyclingMatch() {
    assertThat(TEST_TRANSACTIONS.size()).isAtLeast(EXPENSE_ACCOUNTS.size());
    Iterator<Account> cyclingExpenseAccountIterator = Iterators.cycle(EXPENSE_ACCOUNTS);
    ImmutableMap<Class<?>, Supplier<Result<?>>> resultByPromptClass =
        ImmutableMap.of(
            OptionsPrompt.class,
            () -> Result.string(cyclingExpenseAccountIterator.next().getName()));
    when(mockPromptEvaluator.blockingGetResult(any()))
        .thenAnswer(usingResultCalculatorByPromptClass(resultByPromptClass));
    when(mockSplitMatcher.getTopMatches(any(), any(), any())).thenReturn(ImmutableList.of());
    Model actualOutputModel =
        SplitMatcherScreen.start(
            mockPromptEvaluator,
            mockSplitMatcher,
            /* modelToMatch= */ importedModel(TEST_TRANSACTIONS),
            /* initiallyKnownAccountsById= */ Maps.uniqueIndex(EXPENSE_ACCOUNTS, Account::getId));

    // Verify the prompt evaluator was invoked once for each transaction to match.
    verify(mockPromptEvaluator, times(EXPENSE_ACCOUNTS.size()))
        .blockingGetResult(argThat(IS_A_DECORATED_OPTIONS_PROMPT));
    assertThat(actualOutputModel.getAccountList()).containsExactlyElementsIn(ALL_ACCOUNTS);
    assertThat(actualOutputModel.getTransactionList()).containsExactlyElementsIn(TEST_TRANSACTIONS);
    // Ensure the splits had the correct ammounts and all of the expense accounts were matched up.
    assertThat(actualOutputModel.getSplitList())
        .comparingElementsUsing(SPLIT_WITH_BIGDECIMAL_AMOUNT_COMPARETO_CORRESPONDENCE)
        .containsAnyIn(ImmutableSet.of(DEFAULT_SPLIT_AMOUNT, DEFAULT_SPLIT_AMOUNT.negate()));
    assertThat(actualOutputModel.getSplitList())
        .comparingElementsUsing(SPLIT_WITH_ACCOUNT_ID_CORRESPONDENCE)
        .containsAtLeastElementsIn(FluentIterable.from(ALL_ACCOUNTS).transform(Account::getId));
  }

  @Test
  public void testMultiSplit() {
    Transaction transactionToMultiSplit = TEST_TRANSACTIONS.get(0);
    IndexedModel modelToMatch = importedModel(ImmutableList.of(transactionToMultiSplit));
    BigDecimal multiSplitAmount =
        DEFAULT_SPLIT_AMOUNT.negate().divide(new BigDecimal("2"), RoundingMode.UNNECESSARY);
    ImmutableList<Account> accountsToSplitTo =
        ImmutableList.of(simpleAccount("Expenses:Misc"), simpleAccount("Expenses:Other"));
    Iterator<String> optionResultsInOrder =
        Iterators.forArray(
            Option.SPLIT_MULTIPLE_WAYS.stringRepresentation(),
            Option.SPLIT_MULTIPLE_WAYS.stringRepresentation(),
            Option.DONE.stringRepresentation());
    Iterator<Account> accountsToSplitToIterator = accountsToSplitTo.iterator();
    ImmutableMap<Class<?>, Supplier<Result<?>>> resultCalculatorsByPromptClass =
        ImmutableMap.of(
            OptionsPrompt.class,
            () -> Result.string(optionResultsInOrder.next()),
            AccountPickerPrompt.class,
            () -> Result.account(accountsToSplitToIterator.next()),
            BigDecimalPrompt.class,
            () -> Result.bigDecimal(multiSplitAmount));
    when(mockPromptEvaluator.blockingGetResult(any()))
        .thenAnswer(usingResultCalculatorByPromptClass(resultCalculatorsByPromptClass));
    when(mockSplitMatcher.getTopMatches(any(), any(), any())).thenReturn(ImmutableList.of());
    Model actualOutputModel =
        SplitMatcherScreen.start(
            mockPromptEvaluator,
            mockSplitMatcher,
            /* modelToMatch= */ modelToMatch,
            /* initiallyKnownAccountsById= */ Maps.uniqueIndex(accountsToSplitTo, Account::getId));

    // Verify the prompt evaluator was invoked for all three options.
    verify(mockPromptEvaluator, times(3)).blockingGetResult(argThat(IS_A_DECORATED_OPTIONS_PROMPT));
    assertThat(actualOutputModel.getAccountList())
        .containsExactlyElementsIn(FluentIterable.of(IMPORT_ACCOUNT).append(accountsToSplitTo));
    assertThat(actualOutputModel.getTransactionList()).containsExactly(transactionToMultiSplit);
    assertThat(actualOutputModel.getSplitList())
        .comparingElementsUsing(SPLIT_WITH_BIGDECIMAL_AMOUNT_COMPARETO_CORRESPONDENCE)
        .containsExactly(DEFAULT_SPLIT_AMOUNT, multiSplitAmount, multiSplitAmount);
  }

  private static IndexedModel importedModel(List<Transaction> transactions) {
    return IndexedModel.create(
        Model.newBuilder()
            .addAccount(IMPORT_ACCOUNT)
            .addAllTransaction(transactions)
            .addAllSplit(
                Lists.transform(
                    transactions,
                    (t) ->
                        ModelGenerators.splitBuilderWithAmount(DEFAULT_SPLIT_AMOUNT)
                            .setAccountId(IMPORT_ACCOUNT.getName())
                            .setTransactionId(t.getId())
                            .build()))
            .build());
  }

  // TODO: Find a better way to do this. The wildcard generics are a bit nasty.
  private static Answer<?> usingResultCalculatorByPromptClass(
      ImmutableMap<Class<?>, Supplier<Result<?>>> promptEvaluatorsByPromptClass) {
    return (Answer<Result<?>>)
        invocationOnMock -> {
          Prompt<?> prompt = invocationOnMock.getArgumentAt(0, Prompt.class);
          while (prompt instanceof PromptDecorator<?>) {
            prompt = ((PromptDecorator<?>) prompt).delegate();
          }
          return promptEvaluatorsByPromptClass.getOrDefault(prompt.getClass(), Result::empty).get();
        };
  }
}
