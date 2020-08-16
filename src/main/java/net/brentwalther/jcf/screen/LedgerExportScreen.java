package net.brentwalther.jcf.screen;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;
import net.brentwalther.jcf.util.Formatter;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LedgerExportScreen {

  private static final String ACCOUNT_DELIMITER = ":";

  public static void start(Model currentModel, OutputStream outputStream) {
    // First produce a map of accounts to names like Assets:Investments:VTSAX
    Map<String, String> accountIdToFullString = new HashMap<>();
    for (Account account : currentModel.accountsById.values()) {
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
        while (!account.getParentId().isEmpty()
            && currentModel.accountsById.containsKey(account.getParentId())) {
          account = currentModel.accountsById.get(account.getParentId());
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
      List<Transaction> transactions = new ArrayList<>(currentModel.transactionsById.values());
      transactions.sort(Ordering.natural().onResultOf(t -> t.postDate));
      for (Transaction transaction : transactions) {
        writer.println(
            Formatter.ledgerDate(transaction.postDate) + " * " + transaction.description);

        List<Split> splits =
            new ArrayList<>(currentModel.splitsByTransactionId.get(transaction.id));
        splits.sort(Ordering.natural().reverse().onResultOf(s -> s.amount()));
        for (Split split : splits) {
          writer.println(
              "  "
                  + padString(accountIdToFullString.get(split.accountId), maxAccountNameLength + 2)
                  + Formatter.ledgerCurrency(split.amount()));
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
