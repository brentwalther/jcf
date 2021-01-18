package net.brentwalther.jcf.model;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import java.time.Instant;
import java.util.Arrays;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;

/**
 * An immutable indexed view of a {@link JcfModel.Model} proto. This data structure produces a more
 * easily consumable "database" view of the proto.
 */
public class IndexedModel {

  private final ImmutableMap<String, Account> accountsById;
  private final ImmutableMap<String, Transaction> transactionsById;
  private final ImmutableListMultimap<String, Split> splitsByTransactionId;
  private final Instant creationInstant;

  private IndexedModel(Model model) {
    this.accountsById = Maps.uniqueIndex(model.getAccountList(), Account::getId);
    this.transactionsById = Maps.uniqueIndex(model.getTransactionList(), Transaction::getId);
    this.splitsByTransactionId = Multimaps.index(model.getSplitList(), Split::getTransactionId);
    this.creationInstant = Instant.now();
  }

  public static IndexedModel create(Model model) {
    return new IndexedModel(model);
  }

  public ImmutableCollection<Account> getAllAccounts() {
    return accountsById.values();
  }

  public ImmutableMap<String, Account> immutableAccountsByIdMap() {
    return accountsById;
  }

  public int getTransactionCount() {
    return transactionsById.size();
  }

  public ImmutableCollection<Transaction> getAllTransactions() {
    return transactionsById.values();
  }

  public ImmutableList<Split> splitsForTransaction(Transaction transaction) {
    return splitsByTransactionId.get(transaction.getId());
  }

  public ImmutableCollection<Split> getAllSplits() {
    return splitsByTransactionId.values();
  }

  public Account getAccountById(String accountId) {
    return accountsById.getOrDefault(accountId, ModelGenerators.EMPTY_ACCOUNT);
  }

  public Transaction getTransactionById(String transactionId) {
    return transactionsById.getOrDefault(transactionId, ModelGenerators.EMPTY_TRANSACTION);
  }

  public String stableIdentifier() {
    return Joiner.on(',')
        .join(
            Arrays.asList(
                "creation unix epoch millis " + creationInstant,
                "account count: " + accountsById.size(),
                "transaction account " + transactionsById.size(),
                "split count " + splitsByTransactionId.size()));
  }

  public Model toProto() {
    return ModelGenerators.create(
        accountsById.values(), transactionsById.values(), splitsByTransactionId.values());
  }
}
