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

  private final Map<String, Multiset<Account>> matches;

  private SplitMatcher() {
    matches = new HashMap<>();
  }

  public static SplitMatcher create() {
    return new SplitMatcher();
  }

  public static SplitMatcher create(Model model) {
    SplitMatcher matcher = create();
    Map<String, Transaction> transactions = model.transactionsById;
    Map<String, Account> accounts = model.accountsById;
    for (Split split : model.splitsByTransactionId.values()) {
      matcher.link(
          accounts.get(split.accountId), transactions.get(split.transactionId).description);
    }
    return matcher;
  }

  /** Link the account to the associated transactions description string. */
  public void link(Account account, String description) {
    description = sanitize(description);
    linkInternal(account, description);
    for (String token : tokenize(description)) {
      linkInternal(account, token);
    }
  }

  private void linkInternal(Account account, String match) {
    if (!matches.containsKey(match)) {
      matches.put(match, HashMultiset.create());
    }
    matches.get(match).add(account);
  }

  /**
   * Returns the top matches for a transaction with the specified description. The list is ordered
   * from most to least confident.
   */
  public ImmutableList<Account> getTopMatches(
      String description, ImmutableSet<Account> accountsToExclude) {
    Multiset<Account> matches = HashMultiset.create();
    if (this.matches.containsKey(description)) {
      matches = this.matches.get(description);
    } else {
      for (String token : tokenize(description)) {
        if (this.matches.containsKey(token)) {
          matches.addAll(this.matches.get(token));
        }
      }
    }
    // We don't want to match on the account that's selected. We're trying to see where the
    // cash is going.
    matches.removeAll(accountsToExclude);
    return ImmutableList.sortedCopyOf(
        Comparator.comparingInt(matches::count).reversed(), matches.elementSet());
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
}
