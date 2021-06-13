package net.brentwalther.jcf.screen;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import java.util.Comparator;
import java.util.Optional;
import net.brentwalther.jcf.export.CsvExporter;
import net.brentwalther.jcf.export.JcfModelExporter;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.ModelValidations;
import net.brentwalther.jcf.prompt.FilePrompt;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;

class ModelReviewScreen {

  private static final ImmutableMap<String, Screen> OPTIONS =
      ImmutableMap.<String, Screen>builder()
          .put("Exit", Screen.EXIT)
          .put("Reconcile/Match Splits", Screen.MATCH_SPLITS)
          .put("Merge model", Screen.MERGE_MODEL)
          .put("Export model", Screen.EXPORT_MODEL)
          .put("Export expenses to CSV", Screen.CSV_EXPORT)
          .build();

  static IndexedModel start(PromptEvaluator promptEvaluator, IndexedModel indexedModel) {
    ImmutableList<Account> allAccounts = indexedModel.getAllAccounts().asList();
    String accountList =
        "- Accounts: " + Joiner.on(", ").join(Lists.transform(allAccounts, Account::getName));
    String transactionOverview =
        "- "
            + indexedModel.getTransactionCount()
            + " transactions, of which "
            + getImbalancedTransactionIds(
                    Multimaps.index(indexedModel.getAllSplits(), Split::getTransactionId))
                .size()
            + " are imbalanced.";
    ImmutableList<String> prefaces =
        ImmutableList.of(
            "This model is not yet merged in to the main model. It includes:",
            accountList,
            transactionOverview);

    String selectedOption =
        Optional.ofNullable(
                promptEvaluator.blockingGetResult(
                    PromptDecorator.topStatusBars(
                        OptionsPrompt.builder(OPTIONS.keySet().asList())
                            .withDefaultOption(1)
                            .withPrefaces(prefaces)
                            .build(),
                        ImmutableList.of("Reviewing: " + indexedModel))))
            .orElse(Result.empty())
            .instance()
            .orElse("");

    if (selectedOption.isEmpty() || !OPTIONS.containsKey(selectedOption)) {
      return indexedModel;
    }

    // Assume the option is numeric since we didn't pass in autocomplete options.
    switch (OPTIONS.get(selectedOption)) {
      case MATCH_SPLITS:
        return IndexedModel.create(
            SplitMatcherScreen.start(
                promptEvaluator,
                SplitMatcher.create(indexedModel),
                indexedModel,
                indexedModel.immutableAccountsByIdMap()));
      case EXPORT_MODEL:
        Optional.ofNullable(promptEvaluator.blockingGetResult(FilePrompt.anyFile()))
            .filter(r -> !r.equals(Result.userInterrupt()))
            .flatMap(Result::instance)
            .ifPresent(file -> JcfModelExporter.start(indexedModel.toProto(), file));
        break;
      case CSV_EXPORT:
        Multiset<String> accountIdCounts =
            HashMultiset.create(
                FluentIterable.from(indexedModel.getAllSplits())
                    .transform((split) -> split.getAccountId()));
        Entry<String> mostFrequentlyOccurringAccountId =
            accountIdCounts.entrySet().stream()
                .max(Comparator.comparingInt(Entry::getCount))
                .orElse(Multisets.immutableEntry("", 0));
        if (mostFrequentlyOccurringAccountId.getCount() != indexedModel.getTransactionCount()) {
          promptEvaluator.blockingGetResult(
              NoticePrompt.withMessages(
                  ImmutableList.of(
                      "This model cannot be exported to CSV unambiguously. There must at least *one* account",
                      "that splits every transaction (the primary 'source' of these transactions).",
                      "Please only export CSVs from a model created from a single account")));
        } else {
          Optional<Account> mostFrequentlyOccurringAccountOptional =
              indexedModel.getAccountById(mostFrequentlyOccurringAccountId.getElement());
          checkState(mostFrequentlyOccurringAccountOptional.isPresent());
          Account mostFrequentlyOccurringAccount = mostFrequentlyOccurringAccountOptional.get();
          Optional.ofNullable(promptEvaluator.blockingGetResult(FilePrompt.anyFile()))
              .filter(r -> !r.equals(Result.userInterrupt()))
              .flatMap(Result::instance)
              .ifPresent(
                  file ->
                      CsvExporter.start(
                          indexedModel,
                          file,
                          /* filters= */ ImmutableList.of(
                              exportItem ->
                                  exportItem.account().equals(mostFrequentlyOccurringAccount),
                              exportItem ->
                                  !exportItem
                                      .account()
                                      .getType()
                                      .equals(JcfModel.Account.Type.EXPENSE))));
        }
        break;
      case EXIT:
        break;
    }
    return indexedModel;
  }

  private static ImmutableSet<String> getImbalancedTransactionIds(
      Multimap<String, Split> splitsByTransactionId) {
    ImmutableSet.Builder<String> imbalancedTransactionIds = ImmutableSet.builder();
    for (String id : splitsByTransactionId.keySet()) {
      boolean isBalanced = ModelValidations.areSplitsBalanced(splitsByTransactionId.get(id));
      if (!isBalanced) {
        imbalancedTransactionIds.add(id);
      }
    }
    return imbalancedTransactionIds.build();
  }
}
