package net.brentwalther.jcf.screen;

import com.google.auto.value.AutoValue;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.matcher.SplitMatcher.Match;
import net.brentwalther.jcf.matcher.SplitMatcher.MatchResult;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.prompt.AccountPickerPrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.prompt.PromptBuilder;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.string.Formatter;

public class SplitMatcherScreen {

  /** A singleton account to represent an unknown account, which is a natural imbalance. */
  private static final Account UNMATCHED_PHANTOM_ACCOUNT =
      Account.newBuilder().setId("Imbalance").setName("Imbalance").build();

  private static final String QUAD_SPACE_STRING = "    ";
  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  /** The maximum number of direct account match candidates to show to the user. */
  private static final int MAX_NUM_MATCHES_SHOWN = 9;

  public static Model start(
      PromptEvaluator promptEvaluator,
      SplitMatcher splitMatcher,
      IndexedModel modelToMatch,
      ImmutableMap<String, Account> allInitiallyKnownAccountsById) {
    ImmutableList<Transaction> transactionsToMatch = modelToMatch.getAllTransactions().asList();
    Map<String, Account> allAccountsById = Maps.newHashMap(allInitiallyKnownAccountsById);
    allAccountsById.putAll(modelToMatch.immutableAccountsByIdMap());
    ImmutableList.Builder<Split> allSplits = ImmutableList.builder();
    ImmutableList.Builder<Transaction> allTransactions = ImmutableList.builder();
    for (int i = 0; i < transactionsToMatch.size(); i++) {
      Transaction transaction = transactionsToMatch.get(i);
      List<Split> splitsForTransaction =
          new ArrayList<>(modelToMatch.splitsForTransaction(transaction));
      int desiredNumMatchedSplits = 2;
      while (splitsForTransaction.size() < desiredNumMatchedSplits) {
        ImmutableList<String> allAccountNames =
            ImmutableList.copyOf(Iterables.transform(allAccountsById.values(), Account::getName));

        int maxAmountStringLength =
            splitsForTransaction.stream()
                .map(ModelTransforms::bigDecimalAmountForSplit)
                .map(Formatter::currency)
                .mapToInt(String::length)
                .max()
                .orElse(4);

        ImmutableList<String> statusMessages =
            ImmutableList.of(
                (transactionsToMatch.size() - i) + " left to match (including this one).");
        ImmutableList<String> prefaces =
            ImmutableList.<String>builder()
                .add(
                    Formatter.date(Instant.ofEpochSecond(transaction.getPostDateEpochSecond()))
                        + " - "
                        + transaction.getDescription())
                .addAll(
                    FluentIterable.from(splitsForTransaction)
                        .transform(
                            (split) ->
                                QUAD_SPACE_STRING
                                    + Formatter.truncateOrLeftPadTo(
                                        maxAmountStringLength,
                                        Formatter.currency(
                                            ModelTransforms.bigDecimalAmountForSplit(split)))
                                    + QUAD_SPACE_STRING
                                    + allAccountsById
                                        .getOrDefault(
                                            split.getAccountId(), UNMATCHED_PHANTOM_ACCOUNT)
                                        .getName()))
                .build();

        ImmutableList<Match> matches =
            splitMatcher.getTopMatches(
                transaction,
                /* shouldExcludePredicate= */ account ->
                    FluentIterable.from(splitsForTransaction)
                        .transform(
                            split ->
                                allAccountsById.getOrDefault(
                                    split.getAccountId(), UNMATCHED_PHANTOM_ACCOUNT))
                        .contains(account));

        ImmutableList.Builder<Option> optionsBuilder = ImmutableList.builder();
        // Add all the partial confidence matches ordered by their confidence.
        ImmutableList<Match> orderedMatches =
            FluentIterable.from(matches)
                .filter(match -> match.result().equals(MatchResult.PARTIAL_CONFIDENCE))
                .toSortedList(Match.GREATEST_CONFIDENCE_FIRST_ORDERING);
        optionsBuilder.addAll(
            Lists.transform(
                orderedMatches.subList(0, Math.min(orderedMatches.size(), MAX_NUM_MATCHES_SHOWN)),
                (match) -> Option.create(match.account())));

        // Always allow the user to just leave it unmatched.
        optionsBuilder.add(Option.LEAVE_UNMATCHED);

        // As a last option, give the user the option to skip it if it's probably a duplicate.
        if (matches.stream()
            .anyMatch(match -> match.result().equals(MatchResult.PROBABLE_DUPLICATE))) {
          optionsBuilder.add(Option.SKIP_CONFIRM_DUPLICATE);
        }

        ImmutableMap<String, Option> options =
            Maps.uniqueIndex(optionsBuilder.build(), Option::stringRepresentation);
        Result result =
            promptEvaluator.blockingGetResult(
                PromptDecorator.topStatusBars(
                    OptionsPrompt.builder(options.keySet().asList())
                        .withDefaultOption(1)
                        .withAutoCompleteOptions(allAccountNames)
                        .withPrefaces(prefaces)
                        .build(),
                    statusMessages));

        if (result == null || !result.instance().isPresent() || result == Result.USER_INTERRUPT) {
          LOGGER.atWarning().log(
              "Aborting split matching due to missing input. Did you Ctrl + C ?");
          return ModelGenerators.empty();
        }

        String selectedOption = (String) result.instance().get();

        if (!options.containsKey(selectedOption)) {
          continue;
        }

        Option option = options.get(selectedOption);
        if (option == Option.SPLIT_MULTIPLE_WAYS) {
          Optional.ofNullable(
                  promptEvaluator.blockingGetResult(
                      AccountPickerPrompt.create(allAccountsById.values())))
              .filter(r -> r != Result.USER_INTERRUPT)
              .flatMap(Result::instance)
              .filter(instance -> instance instanceof Account)
              .map(instance -> (Account) instance)
              .ifPresent(
                  account ->
                      Optional.ofNullable(
                              promptEvaluator.blockingGetResult(
                                  PromptBuilder.<BigDecimal>create()
                                      .withPromptString(
                                          "Enter the amount for the split (w/ format [+-][0-9]+[.][0-9]+):")
                                      .withTransformer(
                                          input -> {
                                            try {
                                              return Result.bigDecimal(new BigDecimal(input));
                                            } catch (NumberFormatException e) {
                                              return Result.empty();
                                            }
                                          })
                                      .build()))
                          .flatMap(Result::instance)
                          .filter(instance -> instance instanceof BigDecimal)
                          .map(instance -> (BigDecimal) instance)
                          .ifPresent(
                              splitAmount ->
                                  splitsForTransaction.add(
                                      ModelGenerators.splitBuilderWithAmount(splitAmount)
                                          .setAccountId(account.getId())
                                          .setTransactionId(transaction.getId())
                                          .build())));
        } else if (option == Option.SKIP_CONFIRM_DUPLICATE) {
          // The user has confirmed that this transaction is a duplicate. Go ahead and
          // skip matching it.
          splitsForTransaction.clear();
          break;
        } else if (option == Option.LEAVE_UNMATCHED) {
          desiredNumMatchedSplits--;
        } else if (option.account().isPresent()) {
          Split newSplit =
              ModelGenerators.splitBuilderWithAmount(offsettingAmountOf(splitsForTransaction))
                  .setAccountId(option.account().get().getId())
                  .setTransactionId(transaction.getId())
                  .build();
          splitsForTransaction.add(newSplit);
          splitMatcher.link(transaction, newSplit);
        }
      }

      if (!splitsForTransaction.isEmpty()) {
        allTransactions.add(transaction);
        allSplits.addAll(splitsForTransaction);
      }
    }

    return ModelGenerators.create(
        allAccountsById.values(), allTransactions.build(), allSplits.build());
  }

  /** Returns a BigDecimal which is the negation of the sum of all split amounts. */
  private static BigDecimal offsettingAmountOf(List<Split> splitsForTransaction) {
    return splitsForTransaction.stream()
        .map(ModelTransforms::bigDecimalAmountForSplit)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .negate();
  }

  @AutoValue
  public abstract static class Option {
    public static final Option LEAVE_UNMATCHED = Option.create("Leave unmatched.");
    public static final Option SPLIT_MULTIPLE_WAYS = Option.create("Split multiple ways.");
    public static final Option SKIP_CONFIRM_DUPLICATE = Option.create("Skip. It is a duplicate.");

    private static Option create(String s) {
      return new AutoValue_SplitMatcherScreen_Option(s, Optional.empty());
    }

    public static Option create(Account account) {
      return new AutoValue_SplitMatcherScreen_Option(account.getName(), Optional.of(account));
    }

    protected abstract String stringRepresentation();

    public abstract Optional<Account> account();

    @Override
    public String toString() {
      return stringRepresentation();
    }
  }
}
