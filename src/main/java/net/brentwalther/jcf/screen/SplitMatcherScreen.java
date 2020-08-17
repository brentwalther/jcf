package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.util.Formatter;
import net.brentwalther.jcf.util.ModelUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SplitMatcherScreen {

  private static final Account UNSELECTED_ACCOUNT =
      Account.newBuilder().setId("UNMATCHED").setName("IMBALANCE").build();

  public static void start(
      SplitMatcher splitMatcher, Model model, Iterable<Account> allKnownAccounts) {
    ModelManager.removeModel(model);
    Account account = Iterables.getOnlyElement(model.accountsById.values());
    int totalMatchesToMake = model.transactionsById.size();
    ImmutableSet.Builder<Account> matchedAccounts = ImmutableSet.<Account>builder().add(account);
    List<Split> matches = new ArrayList<>(totalMatchesToMake);
    for (String transactionId : model.transactionsById.keySet()) {
      if (model.splitsByTransactionId.get(transactionId).size() > 1) {
        continue;
      }
      Transaction transaction = model.transactionsById.get(transactionId);
      String transactionDescription = transaction.getDescription();

      ImmutableList<Account> options =
          ImmutableList.<Account>builder()
              .addAll(splitMatcher.getTopMatches(transactionDescription, ImmutableSet.of(account)))
              .add(UNSELECTED_ACCOUNT)
              .build();

      ImmutableMap<String, Account> accountsByName =
          Maps.uniqueIndex(allKnownAccounts, Account::getName);

      Split existingSplit =
          Iterables.getOnlyElement(model.splitsByTransactionId.get(transactionId));
      BigDecimal amount = ModelUtil.toBigDecimal(existingSplit);
      boolean isFlowingOut = amount.compareTo(BigDecimal.ZERO) < 0;
      if (isFlowingOut) {
        amount = amount.negate();
      }

      ImmutableList<String> statusMessages =
          ImmutableList.of(
              (totalMatchesToMake - matches.size()) + " left to match (including this one).");
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
        return;
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
      matches.add(
          Split.newBuilder()
              .setAccountId(chosenAccount.getId())
              .setTransactionId(transactionId)
              .setValueNumerator(-existingSplit.getValueNumerator())
              .setValueDenominator(existingSplit.getValueDenominator())
              .build());
    }

    ImmutableList<Split> existingSplits = model.splitsByTransactionId.values().asList();
    ModelManager.addModel(
        new Model(
            matchedAccounts.build().asList(),
            model.transactionsById.values().asList(),
            ImmutableList.<Split>builderWithExpectedSize(existingSplits.size() + matches.size())
                .addAll(existingSplits)
                .addAll(matches)
                .build()));
  }
}
