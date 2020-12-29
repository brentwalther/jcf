package net.brentwalther.jcf.export;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.flogger.FluentLogger;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.util.Formatter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LedgerExporter {

  private static final String COLON_ACCOUNT_DELIMITER_CHAR = ":";
  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private static final String IMBALANCE_ACCOUNT_NAME = "Imbalance";

  /** Writes the model as a ledger CLI format file to outputStream. Returns true if successful. */
  public static boolean exportToFile(IndexedModel indexedModel, File file) {
    if (file.isFile()) {
      LOGGER.atWarning().log(
          "File to export to already exists. Not overwriting it: %s", file.getAbsolutePath());
      return false;
    }
    if (indexedModel.getAllTransactions().isEmpty()
        && indexedModel.getAllAccounts().isEmpty()
        && indexedModel.getAllSplits().isEmpty()) {
      LOGGER.atWarning().log(
          "Model to export is empty. Not writing a file to: %s", file.getAbsolutePath());
      return false;
    }
    try {
      if (!file.createNewFile()) {
        LOGGER.atSevere().log("Could not create file to export to: %s", file.getAbsolutePath());
        return false;
      }
    } catch (IOException e) {
      LOGGER.atSevere().withCause(e).log(
          "Could not create file to export to: %s", file.getAbsolutePath());
      return false;
    }
    try (OutputStream outputStream = new FileOutputStream(file)) {
      // First produce a map of accounts to names like Assets:Investments:VTSAX
      Map<String, String> accountIdToFullString = new HashMap<>();
      ImmutableMap<String, Account> accountsById = indexedModel.immutableAccountsByIdMap();
      for (Account account : indexedModel.getAllAccounts()) {
        String originalId = account.getId();
        List<String> names = new ArrayList<>(4);
        names.add(account.getName());
        while (!account.getParentId().isEmpty()
            && accountsById.containsKey(account.getParentId())) {
          account = accountsById.get(account.getParentId());
          if (account.getName().isEmpty()) {
            break;
          }
          names.add(account.getName());
        }
        accountIdToFullString.put(
            originalId, Joiner.on(COLON_ACCOUNT_DELIMITER_CHAR).join(Lists.reverse(names)));
      }

      int maxAccountNameLength =
          Math.max(
              accountIdToFullString.values().stream()
                  .mapToInt(String::length)
                  .max()
                  .orElse(IMBALANCE_ACCOUNT_NAME.length()),
              IMBALANCE_ACCOUNT_NAME.length());

      try (PrintWriter writer = new PrintWriter(outputStream)) {
        for (Transaction transaction :
            Ordering.from(Comparator.comparingLong(Transaction::getPostDateEpochSecond))
                .immutableSortedCopy(indexedModel.getAllTransactions())) {
          // TODO: We assume here that all transactions are in a 'cleared' state and denote that
          //   with an asterisk. If we add transaction clear status, this could also be an
          //   exclamation point. See:
          //   https://www.ledger-cli.org/3.0/doc/ledger3.html#index-transaction_002c-automated
          writer.println(
              Formatter.ledgerDate(Instant.ofEpochSecond(transaction.getPostDateEpochSecond()))
                  + " * "
                  + transaction.getDescription());

          for (Split split :
              FluentIterable.from(indexedModel.splitsForTransaction(transaction))
                  .toSortedList(
                      Ordering.natural()
                          .reverse()
                          .onResultOf(ModelTransforms::bigDecimalAmountForSplit))) {
            writer.println(
                "  "
                    + padString(
                        Optional.ofNullable(accountIdToFullString.get(split.getAccountId()))
                            .orElse(IMBALANCE_ACCOUNT_NAME),
                        maxAccountNameLength + 2)
                    + Formatter.ledgerCurrency(ModelTransforms.bigDecimalAmountForSplit(split)));
          }
          writer.println();
        }
      }
    } catch (FileNotFoundException e) {
      LOGGER.atSevere().withCause(e).log(
          "Could not open file to export to: %s", file.getAbsolutePath());
      return false;
    } catch (IOException e) {
      LOGGER.atSevere().withCause(e).log("Export to file '%s' failed.", file.getAbsolutePath());
      return false;
    }
    return true;
  }

  private static String padString(String s, int len) {
    StringBuilder padded = new StringBuilder(s);
    while (padded.length() < len) {
      padded.append(" ");
    }
    return padded.toString();
  }
}
