package net.brentwalther.jcf.matcher;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static net.brentwalther.jcf.model.ModelTransforms.bigDecimalAmountForSplit;

import com.google.auto.value.AutoValue;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;

public class SplitMatcher {

  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
  private static final Pattern DOT_COM_PATTERN = Pattern.compile("[.][Cc][Oo][Mm]");
  private static final Pattern NON_ALPHANUM_CHAR_PATTERN = Pattern.compile("[^0-9A-Za-z]");
  private static final Pattern REPEATED_DIGITS_PATTERN = Pattern.compile("[0-9]{4,25}");
  private static final ImmutableList<Function<String, String>> SANITIZERS =
      ImmutableList.of(
          s -> DOT_COM_PATTERN.matcher(s).replaceAll(""),
          s -> NON_ALPHANUM_CHAR_PATTERN.matcher(s).replaceAll(" "),
          s -> REPEATED_DIGITS_PATTERN.matcher(s).replaceAll(" "));
  /**
   * The maximum expected amount of days two 'probable duplicate' transactions can be separated by.
   */
  private static final long MAXIMUM_EXPECTED_DAYS_FOR_TRANSACTIONS_TO_CLEAR = 7;

  private final ImmutableMap<String, Account> initiallyKnownAccountsById;
  private final ImmutableMap<String, Transaction> initiallyKnownTransactionsById;
  private final SetMultimap<String, Split> transactionDescriptionTokenIndex;
  private final Map<String, Transaction> newlyDiscoveredTransactionsById;

  private SplitMatcher(
      ImmutableMap<String, Account> initiallyKnownAccountsById,
      Iterable<Transaction> allTransactions) {
    this.initiallyKnownAccountsById = initiallyKnownAccountsById;
    this.initiallyKnownTransactionsById = Maps.uniqueIndex(allTransactions, Transaction::getId);
    this.newlyDiscoveredTransactionsById = Maps.newHashMap();
    this.transactionDescriptionTokenIndex = MultimapBuilder.hashKeys().hashSetValues().build();
  }

  public static SplitMatcher create(JcfModel.Model proto) {
    return create(IndexedModel.create(proto));
  }

  public static SplitMatcher create(IndexedModel model) {
    SplitMatcher matcher =
        new SplitMatcher(model.immutableAccountsByIdMap(), model.getAllTransactions());
    for (Transaction transaction : model.getAllTransactions()) {
      for (Split split : model.splitsForTransaction(transaction)) {
        matcher.link(transaction, split);
      }
    }
    return matcher;
  }

  /** Returns the string with junk removed. */
  private static String sanitize(String s) {
    for (Function<String, String> sanitizer : SANITIZERS) {
      s = sanitizer.apply(s);
    }
    return s;
  }

  private static Iterable<String> tokenize(String s) {
    // Return the split tokens. The splitter throws out empty strings.
    return SPACE_SPLITTER.split(s);
  }

  /** Link the account to the associated transactions description string. */
  public void link(Transaction transaction, Split split) {
    if (!initiallyKnownTransactionsById.containsKey(transaction.getId())) {
      newlyDiscoveredTransactionsById.put(transaction.getId(), transaction);
    }
    String sanitizedDescription = sanitize(transaction.getDescription());
    checkState(
        initiallyKnownAccountsById.containsKey(split.getAccountId()),
        "Split refers to an account that doesn't exist: %s",
        split);
    linkInternal(split, sanitizedDescription);
    for (String token : tokenize(sanitizedDescription)) {
      linkInternal(split, token);
    }
  }

  private void linkInternal(Split split, String match) {
    transactionDescriptionTokenIndex.put(match, split);
  }

  /**
   * Returns the top matches for a transaction with the specified description. The list is ordered
   * from most to least confident.
   */
  public ImmutableList<Match> getTopMatches(
      Transaction transaction,
      List<Split> splitsForTransaction,
      ShouldExcludePredicate shouldExcludePredicate) {
    ImmutableList.Builder<Match> matchesBuilder = ImmutableList.builder();

    ImmutableSet.Builder<Split> probableDuplicates = ImmutableSet.builder();
    for (Split existingSplit : transactionDescriptionTokenIndex.values()) {
      // NOTE: This can quickly become slow if splitsForTransaction is very
      // large. In practice it should only contain one split, but maybe a
      // few.
      BigDecimal splitAmount = bigDecimalAmountForSplit(existingSplit);
      for (Split split : splitsForTransaction) {
        boolean hasSameAmount = splitAmount.compareTo(bigDecimalAmountForSplit(split)) == 0;
        boolean hasSameAccount = split.getAccountId().equals(existingSplit.getAccountId());
        boolean postDateIsSimilar =
            Duration.ofSeconds(
                        Math.abs(
                            transactionForSplit(split).getPostDateEpochSecond()
                                - transactionForSplit(existingSplit).getPostDateEpochSecond()))
                    .toDays()
                < MAXIMUM_EXPECTED_DAYS_FOR_TRANSACTIONS_TO_CLEAR;
        if (hasSameAmount && hasSameAccount && postDateIsSimilar) {
          probableDuplicates.add(existingSplit);
        }
      }
    }
    if (!probableDuplicates.build().isEmpty()) {
      matchesBuilder.add(probableDuplicateMatch(probableDuplicates.build().asList()));
    }

    ImmutableMultiset<Split> matches =
        FluentIterable.from(tokenize(sanitize(transaction.getDescription())))
            .transformAndConcat(transactionDescriptionTokenIndex::get)
            .toMultiset();
    int maxCount = Sets.newHashSet(transactionDescriptionTokenIndex.values()).size();
    matchesBuilder.addAll(
        FluentIterable.from(matches.entrySet())
            .filter(
                entry ->
                    entry != null
                        && not(shouldExcludePredicate).apply(accountForSplit(entry.getElement())))
            .transform(
                (entry) ->
                    Match.withProbability(
                        MatchData.create(
                            accountForSplit(entry.getElement()),
                            transactionForSplit(entry.getElement()),
                            entry.getElement()),
                        1.0 * entry.getCount() / maxCount)));
    return matchesBuilder.build();
  }

  private Transaction transactionForSplit(Split split) {
    return initiallyKnownTransactionsById.getOrDefault(
        split.getTransactionId(),
        newlyDiscoveredTransactionsById.getOrDefault(
            split.getTransactionId(), Transaction.getDefaultInstance()));
  }

  private Account accountForSplit(Split split) {
    return initiallyKnownAccountsById.getOrDefault(
        split.getAccountId(), Account.getDefaultInstance());
  }

  private Match probableDuplicateMatch(List<Split> splits) {
    ImmutableList.Builder<MatchData> matchesBuilder =
        ImmutableList.builderWithExpectedSize(splits.size());
    for (Split split : splits) {
      matchesBuilder.add(
          MatchData.create(accountForSplit(split), transactionForSplit(split), split));
    }
    return Match.probableDuplicate(matchesBuilder.build());
  }

  public enum MatchResult {
    PARTIAL_CONFIDENCE,
    PROBABLE_DUPLICATE,
  }

  public interface ShouldExcludePredicate extends Predicate<Account> {}

  @AutoValue
  public abstract static class MatchData {

    public static MatchData create(Account account, Transaction transaction, Split split) {
      return new AutoValue_SplitMatcher_MatchData(account, transaction, split);
    }

    public abstract Account account();

    public abstract Transaction transaction();

    public abstract Split split();
  }

  @AutoValue
  public abstract static class Match {

    public static Match probableDuplicate(ImmutableList<MatchData> duplicates) {
      return new AutoValue_SplitMatcher_Match(
          MatchResult.PROBABLE_DUPLICATE, duplicates, Optional.empty());
    }

    public static Match withProbability(MatchData match, double probability) {
      return new AutoValue_SplitMatcher_Match(
          MatchResult.PARTIAL_CONFIDENCE, ImmutableList.of(match), Optional.of(probability));
    }

    /** The type of the result. Depending on this type, some fields may not be filled in. */
    public abstract MatchResult result();

    /** The metadata associated with this match. Filled for all MatchResult types. */
    public abstract ImmutableList<MatchData> matches();

    /** The confidence of the result. Only present if MatchResult==PARTIAL_CONFIDENCE. */
    public abstract Optional<Double> confidence();
  }
}
