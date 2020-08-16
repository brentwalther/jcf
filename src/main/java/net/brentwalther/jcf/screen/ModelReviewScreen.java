package net.brentwalther.jcf.screen;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.prompt.FilePrompt;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import org.jline.terminal.Terminal;

import java.io.File;
import java.math.BigDecimal;

class ModelReviewScreen {

  private static final ImmutableMap<String, Screen> OPTIONS =
      ImmutableMap.<String, Screen>builder()
          .put("Exit", Screen.EXIT)
          .put("Reconcile/Match Splits", Screen.MATCH_SPLITS)
          .put("Merge model", Screen.MERGE_MODEL)
          .put("Export model", Screen.EXPORT_MODEL)
          .put("Export expenses to CSV", Screen.CSV_EXPORT)
          .build();

  static void start(Model model) {

    ImmutableList<String> optionNames = OPTIONS.keySet().asList();

    Terminal terminal = TerminalProvider.get();
    int maxWidth = terminal.getWidth();

    String accountList =
        "- Accounts: "
            + Joiner.on(", ")
                .join(Lists.transform(model.accountsById.values().asList(), Account::getName));
    String transactionOverview =
        "- "
            + model.transactionsById.size()
            + " transactions, of which "
            + getImbalancedTransactionIds(model.splitsByTransactionId).size()
            + " are imbalanced.";
    ImmutableList<String> prefaces =
        ImmutableList.of(
            "This model is not yet merged in to the main model. It includes:",
            accountList.substring(0, Math.min(accountList.length(), maxWidth)),
            transactionOverview);

    OptionsPrompt.Choice selectedOption;
    do {
      selectedOption =
          PromptEvaluator.showAndGetResult(
              terminal,
              PromptDecorator.decorateWithStatusBars(
                  OptionsPrompt.builder(optionNames)
                      .withDefaultOption(1)
                      .withPrefaces(prefaces)
                      .build(),
                  ImmutableList.of("Reviewing: " + model)));

      if (selectedOption == null) {
        return;
      }
    } while (selectedOption.type == OptionsPrompt.ChoiceType.EMPTY);

    // Assume the option is numeric since we didn't pass in autocomplete options.
    switch (OPTIONS.get(optionNames.get(selectedOption.numberChoice))) {
      case MATCH_SPLITS:
        SplitMatcherScreen.start(
            SplitMatcher.create(ModelManager.getCurrentModel()),
            model,
            ModelManager.getCurrentModel().accountsById.values());
        break;
      case MERGE_MODEL:
        ModelMergerScreen.start(model);
        break;
      case EXPORT_MODEL:
        File modelFile = PromptEvaluator.showAndGetResult(terminal, FilePrompt.anyFile());
        if (modelFile != null && modelFile.exists()) {
          JcfExportScreen.start(model, modelFile);
        }
        break;
      case CSV_EXPORT:
        Multiset<Account> accountCounts =
            HashMultiset.create(
                FluentIterable.from(model.splitsByTransactionId.values())
                    .transform((split) -> model.accountsById.get(split.accountId)));
        Account mostFrequentlyOccuringAccount =
            Iterables.getFirst(Multisets.copyHighestCountFirst(accountCounts), null);
        if (mostFrequentlyOccuringAccount == null
            || accountCounts.count(mostFrequentlyOccuringAccount)
                != model.transactionsById.size()) {
          PromptEvaluator.showAndGetResult(
              TerminalProvider.get(),
              NoticePrompt.withMessages(
                  ImmutableList.of(
                      "The most frequently occuring account in this model is "
                          + mostFrequentlyOccuringAccount.getName(),
                      " however, that account doesn't appear in every split which makes this",
                      " export ambiguous. Please only export CSVs from a model created from a single account")));
        } else {
          File csvFile = PromptEvaluator.showAndGetResult(terminal, FilePrompt.anyFile());
          if (csvFile != null && csvFile.exists()) {
            CsvExportScreen.start(
                model,
                csvFile,
                /* filters= */ ImmutableList.of(
                    exportItem -> exportItem.account().equals(mostFrequentlyOccuringAccount),
                    exportItem ->
                        !exportItem.account().getType().equals(JcfModel.Account.Type.EXPENSE)));
          }
        }
        break;
      case EXIT:
        break;
    }
  }

  private static ImmutableSet<String> getImbalancedTransactionIds(
      Multimap<String, Split> splitsByTransactionId) {
    ImmutableSet.Builder<String> imbalancedTransactionIds = ImmutableSet.builder();
    for (String id : splitsByTransactionId.keySet()) {
      boolean isBalanced =
          splitsByTransactionId.get(id).stream()
                  .map(Split::amount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add)
                  .compareTo(BigDecimal.ZERO)
              == 0;
      if (!isBalanced) {
        imbalancedTransactionIds.add(id);
      }
    }
    return imbalancedTransactionIds.build();
  }
}
