package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.util.Formatter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SplitMatcherScreen {

  private static final Account UNSELECTED_ACCOUNT =
      Account.newBuilder().setId("UNMATCHED").setName("IMBALANCE").build();

  public static Model start(
      SplitMatcher splitMatcher, IndexedModel indexedModel, Iterable<Account> allKnownAccounts) {
    Account account = Iterables.getOnlyElement(indexedModel.getAllAccounts());
    int totalMatchesToMake = indexedModel.getTransactionCount();
    ImmutableSet.Builder<Account> matchedAccounts = ImmutableSet.<Account>builder().add(account);
    List<Split> allSplits = new ArrayList<>(totalMatchesToMake);
    for (Transaction transaction : indexedModel.getAllTransactions()) {
      List<Split> splits = indexedModel.splitsForTransaction(transaction);
      if (splits.size() > 1) {
        continue;
      }
      String transactionDescription = transaction.getDescription();

      ImmutableList<Account> options =
          ImmutableList.<Account>builder()
              .addAll(splitMatcher.getTopMatches(transactionDescription, ImmutableSet.of(account)))
              .add(UNSELECTED_ACCOUNT)
              .build();

      ImmutableMap<String, Account> accountsByName =
          Maps.uniqueIndex(allKnownAccounts, Account::getName);

      Split existingSplit = Iterables.getOnlyElement(splits);
      BigDecimal amount = ModelGenerator.bigDecimalForSplit(existingSplit);
      boolean isFlowingOut = amount.compareTo(BigDecimal.ZERO) < 0;
      if (isFlowingOut) {
        amount = amount.negate();
      }

      ImmutableList<String> statusMessages =
          ImmutableList.of(
              (totalMatchesToMake - allSplits.size()) + " left to match (including this one).");
      ImmutableList<String> prefaces =
          ImmutableList.of(
              "A transaction was made with "
                  + transactionDescription
                  + " on "
                  + Formatter.dateTime(Instant.ofEpochSecond(transaction.getPostDateEpochSecond())),
              Formatter.currency(amount)
                  + " flowed "
                  + (isFlowingOut ? "from " : "to ")
                  + account.getName()
                  + (isFlowingOut ? " to:" : " from:"));

      OptionsPrompt.Choice result =
          PromptEvaluator.showAndGetResult(
              TerminalProvider.get(),
              PromptDecorator.decorateWithStatusBars(
                  OptionsPrompt.builder(Lists.transform(options, Account::getName))
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

      Account chosenAccount = UNSELECTED_ACCOUNT;
      if (result != null) {
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
      }
      if (!chosenAccount.equals(UNSELECTED_ACCOUNT)) {
        splitMatcher.link(chosenAccount, transactionDescription);
      }
      matchedAccounts.add(chosenAccount);
      allSplits.add(existingSplit);
      allSplits.add(
          Split.newBuilder()
              .setAccountId(chosenAccount.getId())
              .setTransactionId(transaction.getId())
              .setValueNumerator(-existingSplit.getValueNumerator())
              .setValueDenominator(existingSplit.getValueDenominator())
              .build());
    }

    return ModelGenerator.create(
        matchedAccounts.build().asList(), indexedModel.getAllTransactions(), allSplits);
  }
}
