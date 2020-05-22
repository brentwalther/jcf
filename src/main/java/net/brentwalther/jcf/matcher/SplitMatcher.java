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

public class SplitMatcher {

  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
  private final Map<String, Multiset<Account>> tokenMatches;

  public SplitMatcher(Model model) {
    this.tokenMatches = createTokenToAccountMappings(model);
  }

  public static SplitMatcher create(Model model) {
    return new SplitMatcher(model);
  }

  private static Map<String, Multiset<Account>> createTokenToAccountMappings(Model model) {
    Map<String, Transaction> transactions = model.transactionsById;
    Map<String, Account> accounts = model.accountsById;
    Map<String, Multiset<Account>> mappings = new HashMap<>();
    for (Split split : model.splitsByTransactionId.values()) {
      String description = transactions.get(split.transactionId).description;
      Account account = accounts.get(split.accountId);
      for (String token : SPACE_SPLITTER.split(description)) {
        if (!mappings.containsKey(token)) {
          mappings.put(token, HashMultiset.create());
        }
        mappings.get(token).add(account);
      }
    }
    return mappings;
  }

  /**
   * Returns the top matches for a transaction with the specified description. The list is ordered
   * from most to least confident.
   */
  public ImmutableList<Account> getTopMatches(
      String description, ImmutableSet<Account> accountsToExclude) {
    Multiset<Account> matches = HashMultiset.create();
    for (String token : SPACE_SPLITTER.split(description)) {
      if (tokenMatches.containsKey(token)) {
        matches.addAll(tokenMatches.get(token));
      }
    }
    // We don't want to match on the account that's selected. We're trying to see where the
    // cash is going.
    matches.removeAll(accountsToExclude);
    return ImmutableList.sortedCopyOf(
        Comparator.comparingInt(matches::count).reversed(), matches.elementSet());
  }
}
