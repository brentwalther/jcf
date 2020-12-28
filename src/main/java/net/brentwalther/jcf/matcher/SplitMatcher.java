package net.brentwalther.jcf.matcher;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;

import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;

public class SplitMatcher {

  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
  private static final Pattern DOT_COM_PATTERN = Pattern.compile("[.][Cc][Oo][Mm]");
  private static final Pattern NON_ALPHANUM_CHAR_PATTERN = Pattern.compile("[^0-9A-Za-z]");
  private static final Pattern REPEATED_DIGITS_PATTERN = Pattern.compile("[0-9]{4,25}");
  private static final Account INVALID_ACCOUNT = Account.newBuilder().setId("INVALID").build();
  private final ImmutableMap<String, Account> initiallyKnownAccountsById;
  private final ImmutableMap<String, Transaction> initiallyKnownTransactionsById;
  private final SetMultimap<String, Split> transactionDescriptionTokenIndex;
  private final Map<String, Transaction> newlyDiscoveredTransactionsById;

  private SplitMatcher(
      ImmutableMap<String, Account> initiallyKnownAccountsById,
      Iterable<Transaction> allTransactions) {
    this.initiallyKnownAccountsById = initiallyKnownAccountsById;
    initiallyKnownTransactionsById = Maps.uniqueIndex(allTransactions, Transaction::getId);
    newlyDiscoveredTransactionsById = Maps.newHashMap();
    transactionDescriptionTokenIndex = MultimapBuilder.hashKeys().hashSetValues().build();
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
    s = DOT_COM_PATTERN.matcher(s).replaceAll("");
    s = REPEATED_DIGITS_PATTERN.matcher(s).replaceAll("");
    return NON_ALPHANUM_CHAR_PATTERN.matcher(s).replaceAll(" ");
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
      Transaction transaction, ImmutableSet<Account> accountsToExclude) {
    ImmutableList.Builder<Match> matchesBuilder = ImmutableList.builder();

    for (Split split : transactionDescriptionTokenIndex.get(transaction.getDescription())) {
      if (!Sets.union(
                  initiallyKnownTransactionsById.keySet(), newlyDiscoveredTransactionsById.keySet())
              .contains(split.getTransactionId())
          || !initiallyKnownAccountsById.containsKey(split.getAccountId())) {
        // We don't know what transaction or account this split belongs to. Disregard it.
        continue;
      }
      Transaction existingTransaction =
          initiallyKnownTransactionsById.containsKey(split.getTransactionId())
              ? initiallyKnownTransactionsById.get(split.getTransactionId())
              : newlyDiscoveredTransactionsById.get(split.getTransactionId());
      if (transaction.getPostDateEpochSecond() == existingTransaction.getPostDateEpochSecond()
          && transaction.getDescription().equals(existingTransaction.getDescription())) {
        matchesBuilder.add(
            Match.probableDuplicate(initiallyKnownAccountsById.get(split.getAccountId())));
      }
    }

    Multiset<Account> potentialAccounts = HashMultiset.create();
    for (String token : tokenize(transaction.getDescription())) {
      for (Split split : transactionDescriptionTokenIndex.get(token)) {
        potentialAccounts.add(
            initiallyKnownAccountsById.getOrDefault(split.getAccountId(), INVALID_ACCOUNT));
      }
    }
    for (Account accountToRemove :
        Iterables.concat(accountsToExclude, ImmutableList.of(INVALID_ACCOUNT))) {
      potentialAccounts.setCount(accountToRemove, 0);
    }
    int maxCount = potentialAccounts.entrySet().stream().mapToInt(Entry::getCount).max().orElse(1);
    matchesBuilder.addAll(
        FluentIterable.from(potentialAccounts.entrySet())
            .transform(
                (entry) ->
                    Match.withProbability(entry.getElement(), 1.0 * entry.getCount() / maxCount)));
    return matchesBuilder.build();
  }

  public enum MatchResult {
    PARTIAL_CONFIDENCE,
    PROBABLE_DUPLICATE,
  }

  @AutoValue
  public abstract static class Match {
    public static final Comparator<Match> GREATEST_CONFIDENCE_FIRST_ORDERING =
        Ordering.natural().reverse().onResultOf(match -> match.confidence());

    public static Match probableDuplicate(Account account) {
      return new AutoValue_SplitMatcher_Match(
          MatchResult.PROBABLE_DUPLICATE, account, /* confidence= */ 1.0);
    }

    public static Match withProbability(Account account, double probability) {
      return new AutoValue_SplitMatcher_Match(MatchResult.PARTIAL_CONFIDENCE, account, probability);
    }

    /** The type of the result. Depending on this type, some fields may not be filled in. */
    public abstract MatchResult result();

    /** The account associated with this match. Filled for all MatchResult types. */
    public abstract Account account();

    /** The confidence of the result. Not guaranteed to be meaningful. */
    public abstract Double confidence();
  }
}
