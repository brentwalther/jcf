package net.brentwalther.jcf.screen;

import com.google.auto.value.AutoValue;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import net.brentwalther.jcf.TerminalProvider;
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
import net.brentwalther.jcf.prompt.PromptBuilder;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.util.Formatter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SplitMatcherScreen {

  /** A singleton account to represent an unknown account, which is a natural imbalance. */
  private static final Account UNMATCHED_PHANTOM_ACCOUNT =
      Account.newBuilder().setId("Imbalance").setName("Imbalance").build();

  private static final String QUAD_SPACE_STRING = "    ";
  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  /** The maximum number of direct account match candidates to show to the user. */
  private static final int MAX_NUM_MATCHES_SHOWN = 9;

  public static Model start(
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

        ImmutableList<Option> options = optionsBuilder.build();
        OptionsPrompt.Choice result =
            PromptEvaluator.showAndGetResult(
                TerminalProvider.get(),
                PromptDecorator.decorateWithStatusBars(
                    OptionsPrompt.builder(options)
                        .withDefaultOption(1)
                        .withAutoCompleteOptions(allAccountNames)
                        .withPrefaces(prefaces)
                        .build(),
                    statusMessages));

        if (result == null) {
          LOGGER.atWarning().log(
              "Aborting split matching due to missing input. Did you Ctrl + C ?");
          return ModelGenerators.empty();
        }

        Option option = null;
        switch (result.type) {
          case EMPTY:
            // Nothing to do, the default option is set to null.
            break;
          case NUMBERED_OPTION:
            option = options.get(result.numberChoice);
            break;
          case AUTOCOMPLETE_OPTION:
            Optional<Account> account =
                allAccountsById.values().stream()
                    .filter(
                        knownAccount -> knownAccount.getName().equals(result.autocompleteChoice))
                    .findFirst();
            if (account.isPresent()) {
              option = Option.create(account.get());
            }
            break;
        }

        if (option == Option.SPLIT_MULTIPLE_WAYS) {
          Account account =
              PromptEvaluator.showAndGetResult(
                  TerminalProvider.get(), AccountPickerPrompt.create(allAccountsById.values()));
          if (account == null) {
            account = UNMATCHED_PHANTOM_ACCOUNT;
          }

          BigDecimal splitAmount =
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
          if (splitAmount != null) {
            splitsForTransaction.add(
                ModelGenerators.splitBuilderWithAmount(splitAmount)
                    .setAccountId(account.getId())
                    .setTransactionId(transaction.getId())
                    .build());
          }
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
