package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.util.Formatter;
import org.jline.terminal.Terminal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SplitMatcherScreen {

  private static final Account UNSELECTED_ACCOUNT =
      new Account("UNMATCHED", "Imbalance (UNKNOWN)", Account.Type.EXPENSE, "");

  public static void start(
      SplitMatcher splitMatcher, Model model, Iterable<Account> allKnownAccounts) {
    ModelManager.removeModel(model);
    Terminal terminal = TerminalProvider.get();
    Account account = Iterables.getOnlyElement(model.accountsById.values());
    int totalMatchesToMake = model.transactionsById.size();
    ImmutableSet.Builder<Account> matchedAccounts = ImmutableSet.<Account>builder().add(account);
    List<Split> matches = new ArrayList<>(totalMatchesToMake);
    for (String transactionId : model.transactionsById.keySet()) {
      if (model.splitsByTransactionId.get(transactionId).size() > 1) {
        continue;
      }
      Transaction transaction = model.transactionsById.get(transactionId);
      String transactionDescription = transaction.description;

      ImmutableList<Account> options =
          ImmutableList.<Account>builder()
              .addAll(splitMatcher.getTopMatches(transactionDescription, ImmutableSet.of(account)))
              .add(UNSELECTED_ACCOUNT)
              .build();

      ImmutableMap<String, Account> accountsByName =
          Maps.uniqueIndex(allKnownAccounts, a -> a.name);

      Split existingSplit =
          Iterables.getOnlyElement(model.splitsByTransactionId.get(transactionId));
      BigDecimal amount = existingSplit.amount();
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
                  + Formatter.dateTime(transaction.postDate),
              "  "
                  + Formatter.currency(amount)
                  + " flowed "
                  + (isFlowingOut ? "from " : "to ")
                  + account.name
                  + (isFlowingOut ? " to:" : " from:"));

      OptionsPrompt.Choice result =
          PromptEvaluator.showAndGetResult(
              terminal,
              PromptDecorator.decorateWithStatusBars(
                  OptionsPrompt.builder(Lists.transform(options, (candidate) -> candidate.name))
                      .withDefaultOption(1)
                      .withAutoCompleteOptions(accountsByName.keySet())
                      .withPrefaces(prefaces)
                      .build(),
                  statusMessages));

      Account chosenAccount = UNSELECTED_ACCOUNT;
      if (result != null) {
        switch (result.type) {
          case EMPTY:
            // Chosen is already set to UNSELECTED.
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
          new Split(
              chosenAccount.id,
              transactionId,
              -existingSplit.valueNumerator,
              existingSplit.valueDenominator));
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
