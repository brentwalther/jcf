package net.brentwalther.jcf.screen;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerator;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.PromptBuilder;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.prompt.SpecialCharacters;
import net.brentwalther.jcf.util.Formatter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Strings.repeat;

public class SplitMatcherScreen {

  private static final Account UNMATCHED_PHANTOM_ACCOUNT =
      Account.newBuilder().setId("Imbalance").setName("Imbalance").build();

  private static final Account MULTISPLIT_SYNTHESIZED_ACCOUNT_OPTION =
      Account.newBuilder().setId("MULTISPLIT").setName("Split multiple ways...").build();
  private static final String QUAD_SPACE_STRING = "    ";

  public static Model start(
      SplitMatcher splitMatcher, IndexedModel modelToMatch, Iterable<Account> allKnownAccounts) {
    ImmutableList<Transaction> transactionsToMatch = modelToMatch.getAllTransactions().asList();
    Map<String, Account> allAccountsById = Maps.newHashMap(modelToMatch.immutableAccountsByIdMap());
    ImmutableList.Builder<Split> allSplits =
        ImmutableList.builderWithExpectedSize(transactionsToMatch.size() * 2);
    for (int i = 0; i < transactionsToMatch.size(); i++) {
      Transaction transaction = transactionsToMatch.get(i);
      List<Split> splitsForTransaction =
          new ArrayList<>(modelToMatch.splitsForTransaction(transaction));
      int desiredNumSplits = 2;
      while (splitsForTransaction.size() < desiredNumSplits) {
        String transactionDescription = transaction.getDescription();
        int longestAmountString =
            splitsForTransaction.stream()
                .map(ModelTransforms::bigDecimalAmountForSplit)
                .map(Formatter::currency)
                .mapToInt(String::length)
                .max()
                .orElse(4);

        ImmutableList<Account> options =
            ImmutableList.<Account>builder()
                .addAll(
                    splitMatcher.getTopMatches(
                        transactionDescription,
                        ImmutableSet.copyOf(
                            Lists.transform(
                                splitsForTransaction,
                                split ->
                                    allAccountsById.getOrDefault(
                                        split.getAccountId(), UNMATCHED_PHANTOM_ACCOUNT)))))
                .add(UNMATCHED_PHANTOM_ACCOUNT)
                .add(MULTISPLIT_SYNTHESIZED_ACCOUNT_OPTION)
                .build();

        ImmutableMap<String, Account> accountsByName =
            Maps.uniqueIndex(allKnownAccounts, Account::getName);

        ImmutableList<String> statusMessages =
            ImmutableList.of(
                (transactionsToMatch.size() - i) + " left to match (including this one).");
        ImmutableList<String> prefaces =
            ImmutableList.<String>builder()
                .add(
                    Formatter.date(Instant.ofEpochSecond(transaction.getPostDateEpochSecond()))
                        + " - "
                        + transactionDescription)
                .addAll(
                    FluentIterable.from(splitsForTransaction)
                        .transform(
                            (split ->
                                QUAD_SPACE_STRING
                                    + Formatter.truncateOrLeftPadTo(
                                        longestAmountString,
                                        Formatter.currency(
                                            ModelTransforms.bigDecimalAmountForSplit(split)))
                                    + QUAD_SPACE_STRING
                                    + allAccountsById
                                        .getOrDefault(
                                            split.getAccountId(), UNMATCHED_PHANTOM_ACCOUNT)
                                        .getName())))
                .add(
                    QUAD_SPACE_STRING
                        + repeat(SpecialCharacters.HORIZONTAL_LINE, longestAmountString + 4))
                .build();

        final int currentNumSplitsNeeded = desiredNumSplits;
        OptionsPrompt.Choice result =
            PromptEvaluator.showAndGetResult(
                TerminalProvider.get(),
                PromptDecorator.decorateWithStatusBars(
                    OptionsPrompt.builder(
                            Lists.transform(
                                options,
                                accountOption ->
                                    accountOption.equals(MULTISPLIT_SYNTHESIZED_ACCOUNT_OPTION)
                                        ? "Split more than " + currentNumSplitsNeeded + " ways."
                                        : accountOption.getName()))
                        .withDefaultOption(1)
                        .withAutoCompleteOptions(accountsByName.keySet())
                        .withPrefaces(prefaces)
                        .build(),
                    statusMessages));

        if (result == null) {
          PromptEvaluator.showAndGetResult(
              TerminalProvider.get(),
              NoticePrompt.withMessages(ImmutableList.of("Aborting split matching.")));
          return ModelGenerator.empty();
        }

        Account chosenAccount = UNMATCHED_PHANTOM_ACCOUNT;
        switch (result.type) {
          case EMPTY:
            // Nothing to do, the default chosenAccount is set to UNSELECTED.
            break;
          case NUMBERED_OPTION:
            chosenAccount = options.get(result.numberChoice);
            break;
          case AUTOCOMPLETE_OPTION:
            chosenAccount = accountsByName.get(result.autocompleteChoice);
            break;
        }
        if (chosenAccount.equals(MULTISPLIT_SYNTHESIZED_ACCOUNT_OPTION)) {
          desiredNumSplits++;
        }
        if (!chosenAccount.equals(UNMATCHED_PHANTOM_ACCOUNT)) {
          splitMatcher.link(chosenAccount, transactionDescription);
        }
        allAccountsById.put(chosenAccount.getId(), chosenAccount);
        BigDecimal splitAmount = BigDecimal.ZERO;
        if (desiredNumSplits == splitsForTransaction.size() + 1) {
          // This last split is apparently all we need for now, so go ahead and assume
          // it takes the remaining amount.
          splitAmount =
              splitsForTransaction.stream()
                  .map(ModelTransforms::bigDecimalAmountForSplit)
                  .reduce(BigDecimal.ZERO, BigDecimal::add)
                  .negate();
        } else {
          BigDecimal convertedInput =
              PromptEvaluator.showAndGetResult(
                  TerminalProvider.get(),
                  PromptBuilder.<BigDecimal>create()
                      .withPromptString(
                          "Enter the amount for the split (w/ format [+-][0-9]+[.][0-9]+):")
                      .withTransformer(
                          input -> {
                            try {
                              return Optional.of(new BigDecimal(input));
                            } catch (NumberFormatException e) {
                              return Optional.empty();
                            }
                          })
                      .build());
          if (convertedInput != null) {
            splitAmount = convertedInput;
          }
        }
        splitsForTransaction.add(
            ModelGenerator.splitBuilderWithAmount(splitAmount)
                .setAccountId(chosenAccount.getId())
                .setTransactionId(transaction.getId())
                .build());
      }
      allSplits.addAll(splitsForTransaction);
    }

    return ModelGenerator.create(
        allAccountsById.values(), modelToMatch.getAllTransactions(), allSplits.build());
  }
}
