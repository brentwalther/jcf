package net.brentwalther.jcf.model;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Account.Type;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.string.Formatter;

public class ModelGenerators {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  private static final ImmutableMap<String, Type> ACCOUNT_TYPES_BY_LOWERCASE_NAME_PREFIXES =
      ImmutableMap.<String, Type>builder()
          .put("asset", Type.ASSET)
          .put("liability", Type.LIABILITY)
          .put("liabilities", Type.LIABILITY)
          .put("income", Type.INCOME)
          .put("expense", Type.EXPENSE)
          .put("equity", Type.EQUITY)
          .build();

  public static Model empty() {
    return ModelGenerators.create(ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
  }

  public static Model create(
      Iterable<Account> accounts, Iterable<Transaction> transactions, Iterable<Split> splits) {
    return Model.newBuilder()
        .setId(
            Hashing.goodFastHash(128)
                .newHasher()
                .putInt(accounts.hashCode())
                .putInt(transactions.hashCode())
                .putInt(splits.hashCode())
                .putInt(Instant.now().hashCode())
                .hash()
                .toString())
        .setCreatedOnEpochSecond(Instant.now().getEpochSecond())
        .addAllAccount(accounts)
        .addAllTransaction(transactions)
        .addAllSplit(splits)
        .build();
  }

  public static PendingMerge merge(Model modelToMerge) {
    return (base) -> {
      Map<String, Account> allAccountsById =
          Maps.newHashMapWithExpectedSize(base.getAccountCount() + modelToMerge.getAccountCount());
      Iterable<Account> unmergedAccounts =
          FluentIterable.concat(base.getAccountList(), modelToMerge.getAccountList());
      for (Account account : unmergedAccounts) {
        String accountId = account.getId();
        if (accountId.isEmpty()) {
          accountId = Hashing.goodFastHash(128).hashBytes(account.toByteArray()).toString();
          account = account.toBuilder().setId(accountId).build();
          LOGGER.atInfo().log("Generated ID %s for account [%s]", accountId, account);
        }
        if (allAccountsById.containsKey(accountId)
            && !allAccountsById.get(accountId).equals(account)) {
          LOGGER.atInfo().log(
              "Overwriting account %s with %s.", allAccountsById.get(accountId), account);
        }
        allAccountsById.put(accountId, account);
      }
      Map<String, Transaction> allTransactionsById =
          Maps.newHashMapWithExpectedSize(
              base.getTransactionCount() + modelToMerge.getTransactionCount());
      Iterable<Transaction> unmergedTransactions =
          FluentIterable.concat(base.getTransactionList(), modelToMerge.getTransactionList());
      for (Transaction transaction : unmergedTransactions) {
        String transactionId = transaction.getId();
        if (transactionId.isEmpty()) {
          transactionId = Hashing.goodFastHash(128).hashBytes(transaction.toByteArray()).toString();
          transaction = transaction.toBuilder().setId(transactionId).build();
          LOGGER.atInfo().log("Generated ID %s for transaction [%s]", transactionId, transaction);
        }
        if (allTransactionsById.containsKey(transactionId)
            && !allTransactionsById.get(transactionId).equals(transaction)) {
          LOGGER.atInfo().log(
              "Overwriting %s with %s.", allTransactionsById.get(transactionId), transaction);
        }
        allTransactionsById.put(transactionId, transaction);
      }
      SetMultimap<String, Split> allSplitsByTransactionId =
          MultimapBuilder.hashKeys(allTransactionsById.size())
              .hashSetValues(/*expectedValuesPerKey=*/ 4)
              .build();
      Iterable<Split> unmergedSplits =
          FluentIterable.concat(base.getSplitList(), modelToMerge.getSplitList());
      for (Split split : unmergedSplits) {
        List<String> badRefs = new ArrayList<>(2);
        if (!allTransactionsById.containsKey(split.getTransactionId())) {
          badRefs.add("transaction");
        }
        if (!allAccountsById.containsKey(split.getAccountId())) {
          badRefs.add("account");
        }
        if (!badRefs.isEmpty()) {
          LOGGER.atWarning().log(
              "Split has bad ID for references to: %s. Dropping it: [accountId: %s, transactionId: %s]",
              Joiner.on(',').join(badRefs), split.getAccountId(), split.getTransactionId());
          continue;
        }
        allSplitsByTransactionId.put(split.getTransactionId(), split);
      }
      for (String transactionId : allSplitsByTransactionId.keySet()) {
        if (!ModelValidations.areSplitsBalanced(allSplitsByTransactionId.get(transactionId))) {
          BigDecimal balance =
              allSplitsByTransactionId.get(transactionId).stream()
                  .map(ModelTransforms::bigDecimalAmountForSplit)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          LOGGER.atWarning().log(
              "Splits are not balanced for transaction [%s]! Current balance: [%s]",
              allTransactionsById.get(transactionId), Formatter.ledgerCurrency(balance));
        }
      }
      return create(
          allAccountsById.values(),
          allTransactionsById.values(),
          allSplitsByTransactionId.values());
    };
  }

  public static Split.Builder splitBuilderWithAmount(BigDecimal amount) {
    int scale = amount.scale();
    if (scale <= 0) {
      return Split.newBuilder()
          .setValueNumerator(amount.toBigInteger().intValueExact())
          .setValueDenominator(1);
    } else {
      BigInteger denominator = BigInteger.TEN.pow(scale);
      BigInteger numerator = amount.unscaledValue();
      BigInteger d = numerator.gcd(denominator);
      return Split.newBuilder()
          .setValueNumerator(numerator.divide(d).intValueExact())
          .setValueDenominator(denominator.divide(d).intValueExact());
    }
  }

  public static Account simpleAccount(String accountName) {
    accountName = accountName.trim();
    return Account.newBuilder()
        .setId(accountName)
        .setName(accountName)
        .setType(guessAccountTypeFromName(accountName))
        .build();
  }

  private static Account.Type guessAccountTypeFromName(String accountName) {
    if (Strings.isNullOrEmpty(accountName)) {
      return Type.UNKNOWN_TYPE;
    }
    return FluentIterable.from(ACCOUNT_TYPES_BY_LOWERCASE_NAME_PREFIXES.entrySet())
        .firstMatch(entry -> accountName.toLowerCase().startsWith(entry.getKey()))
        .transform(Entry::getValue)
        .or(Type.UNKNOWN_TYPE);
  }

  /** A callable interface representing a pending merge into a supplied base model. */
  public interface PendingMerge {
    Model into(Model mergeBase);
  }
}
