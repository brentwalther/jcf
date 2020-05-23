package net.brentwalther.jcf.matcher;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SplitMatcher {

  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
  private static final Pattern DOT_COM_PATTERN = Pattern.compile("[.][Cc][Oo][Mm]");
  private static final Pattern NON_ALPHANUM_CHAR_PATTERN = Pattern.compile("[^0-9A-Za-z]");
  private static final Pattern REPEATED_DIGITS_PATTERN = Pattern.compile("[0-9]{4,25}");


  private final Map<String, Multiset<Account>> fullMatches;
  private final Map<String, Multiset<Account>> tokenMatches;

  public SplitMatcher(Model model) {
    this.fullMatches = createFullDescToAccountMappings(model);
    this.tokenMatches = createTokenToAccountMappings(model);
  }

  public static SplitMatcher create(Model model) {
    return new SplitMatcher(model);
  }

  private static Map<String, Multiset<Account>> createFullDescToAccountMappings(Model model) {
    Map<String, Transaction> transactions = model.transactionsById;
    Map<String, Account> accounts = model.accountsById;
    Map<String, Multiset<Account>> mappings = new HashMap<>();
    for (Split split : model.splitsByTransactionId.values()) {
      String description = transactions.get(split.transactionId).description;
      Account account = accounts.get(split.accountId);
      if (!mappings.containsKey(description)) {
        mappings.put(description, HashMultiset.create());
      }
      mappings.get(description).add(account);
    }
    return mappings;
  }

  private static Map<String, Multiset<Account>> createTokenToAccountMappings(Model model) {
    Map<String, Transaction> transactions = model.transactionsById;
    Map<String, Account> accounts = model.accountsById;
    Map<String, Multiset<Account>> mappings = new HashMap<>();
    for (Split split : model.splitsByTransactionId.values()) {
      String description = transactions.get(split.transactionId).description;
      Account account = accounts.get(split.accountId);
      for (String token : tokenize(description)) {
        if (!mappings.containsKey(token)) {
          mappings.put(token, HashMultiset.create());
        }
        mappings.get(token).add(account);
      }
    }
    return mappings;
  }

  private static Iterable<String> tokenize(String s) {
    // Remove junk
    s = DOT_COM_PATTERN.matcher(s).replaceAll("");
    s = REPEATED_DIGITS_PATTERN.matcher(s).replaceAll("");
    s = NON_ALPHANUM_CHAR_PATTERN.matcher(s).replaceAll(" ");
    // Return the split tokens. The splitter throws out empty strings.
    return SPACE_SPLITTER.split(s);
  }

  /**
   * Returns the top matches for a transaction with the specified description. The list is ordered
   * from most to least confident.
   */
  public ImmutableList<Account> getTopMatches(
      String description, ImmutableSet<Account> accountsToExclude) {
    Multiset<Account> matches = HashMultiset.create();
    if (fullMatches.containsKey(description)) {
      matches = fullMatches.get(description);
    } else {
      for (String token : tokenize(description)) {
        if (tokenMatches.containsKey(token)) {
          matches.addAll(tokenMatches.get(token));
        }
      }
    }
    // We don't want to match on the account that's selected. We're trying to see where the
    // cash is going.
    matches.removeAll(accountsToExclude);
    return ImmutableList.sortedCopyOf(
        Comparator.comparingInt(matches::count).reversed(), matches.elementSet());
  }
}
