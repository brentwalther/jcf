package net.brentwalther.jcf.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.hash.Hashing;
import net.brentwalther.jcf.util.Formatter;

import java.time.Instant;
import java.util.Map;

public class Model {

  public final String id;
  public final ImmutableMap<String, Account> accountsById;
  public final ImmutableMap<String, Transaction> transactionsById;
  public final ImmutableMultimap<String, Split> splitsByTransactionId;
  private final Instant createdTime;

  public Model(
      Map<String, Account> accountsById,
      Map<String, Transaction> transactionsById,
      Multimap<String, Split> splitsByTransactionId) {
    this.createdTime = Instant.now();
    this.id =
        Hashing.goodFastHash(128)
            .newHasher()
            .putInt(accountsById.hashCode())
            .putInt(transactionsById.hashCode())
            .putInt(splitsByTransactionId.hashCode())
            .putInt(createdTime.hashCode())
            .hash()
            .toString();
    this.accountsById = ImmutableMap.copyOf(accountsById);
    this.transactionsById = ImmutableMap.copyOf(transactionsById);
    this.splitsByTransactionId = ImmutableMultimap.copyOf(splitsByTransactionId);
  }

  public Model(
      Iterable<Account> accounts, Iterable<Transaction> transactions, Iterable<Split> splits) {
    this(
        Maps.uniqueIndex(accounts, (account) -> account.id),
        Maps.uniqueIndex(transactions, (transaction) -> transaction.id),
        Multimaps.index(splits, (split) -> split.transactionId));
  }

  public static Model empty() {
    return new Model(ImmutableMap.of(), ImmutableMap.of(), ImmutableMultimap.of());
  }

  @Override
  public String toString() {
    return "Model created on "
        + Formatter.dateTime(createdTime)
        + " with "
        + accountsById.size()
        + " accounts, "
        + transactionsById.size()
        + " transactions, "
        + splitsByTransactionId.size()
        + " splits";
  }
}
