package net.brentwalther.jcf.screen;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.util.Formatter;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LedgerExportScreen {

  private static final String ACCOUNT_DELIMITER = ":";

  public static void start(IndexedModel indexedModel, OutputStream outputStream) {
    // First produce a map of accounts to names like Assets:Investments:VTSAX
    Map<String, String> accountIdToFullString = new HashMap<>();
    for (Account account : indexedModel.getAllAccounts()) {
      if (accountIdToFullString.containsKey(account.getParentId())) {
        accountIdToFullString.put(
            account.getId(),
            accountIdToFullString.get(account.getParentId())
                + ACCOUNT_DELIMITER
                + account.getName());
      } else {
        // We haven't produced the name for this account or any parent yet. Just produce the whole
        // thing.
        String originalId = account.getId();
        List<String> names = new ArrayList<>(4);
        names.add(account.getName());
        while (!account.getParentId().isEmpty()) {
          account = indexedModel.getAccountById(account.getParentId());
          if (account.getName().isEmpty()) {
            break;
          }
          names.add(account.getName());
        }
        accountIdToFullString.put(
            originalId, Joiner.on(ACCOUNT_DELIMITER).join(Lists.reverse(names)));
      }
    }

    int maxAccountNameLength =
        accountIdToFullString.values().stream().mapToInt(String::length).max().orElse(0);

    Terminal terminal = TerminalProvider.get();
    try (PrintWriter writer = new PrintWriter(outputStream)) {
      List<Transaction> transactions = new ArrayList<>(indexedModel.getAllTransactions());
      transactions.sort(Comparator.comparingLong(Transaction::getPostDateEpochSecond));
      for (Transaction transaction : transactions) {
        // TODO: We assume here that all transactions are in a 'cleared' state and denote that
        //   with an asterisk. If we add transaction clear status, this could also be an
        //   exclamation point. See:
        //   https://www.ledger-cli.org/3.0/doc/ledger3.html#index-transaction_002c-automated
        writer.println(
            Formatter.ledgerDate(Instant.ofEpochSecond(transaction.getPostDateEpochSecond()))
                + " * "
                + transaction.getDescription());

        List<Split> splits = new ArrayList<>(indexedModel.splitsForTransaction(transaction));
        splits.sort(
            Ordering.natural().reverse().onResultOf(ModelTransforms::bigDecimalAmountForSplit));
        for (Split split : splits) {
          writer.println(
              "  "
                  + padString(
                      accountIdToFullString.get(split.getAccountId()), maxAccountNameLength + 2)
                  + Formatter.ledgerCurrency(ModelTransforms.bigDecimalAmountForSplit(split)));
        }
        writer.println();
      }
    }
    try {
      new LineReaderImpl(terminal).readLine("Export complete. Press any key to continue...");
    } catch (IOException e) {
      /* do nothing. */
    }
  }

  private static String padString(String s, int len) {
    StringBuilder padded = new StringBuilder(s);
    while (padded.length() < len) {
      padded.append(" ");
    }
    return padded.toString();
  }
}
