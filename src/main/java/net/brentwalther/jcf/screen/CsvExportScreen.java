package net.brentwalther.jcf.screen;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.util.Formatter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CsvExportScreen {

  public static void start(Model model, File csvFile, Iterable<ExportFilter> filters) {
    try {
      List<ExportItem> exportItems = new ArrayList<>();
      for (Split split : model.splitsByTransactionId.values()) {
        ExportItem exportItem =
            new ExportItem() {
              @Override
              public Account account() {
                return model.accountsById.get(split.accountId);
              }

              @Override
              public Transaction transaction() {
                return model.transactionsById.get(split.transactionId);
              }

              @Override
              public Split split() {
                return split;
              }
            };
        if (Iterables.any(filters, (filter) -> filter.shouldExclude(exportItem))) {
          continue;
        }
        exportItems.add(exportItem);
      }

      // Sort them by their post date.
      Collections.sort(
          exportItems,
          Ordering.natural().onResultOf((exportItem) -> exportItem.transaction().postDate));

      PrintWriter printWriter = new PrintWriter(csvFile);
      Joiner joiner = Joiner.on(",");
      for (ExportItem exportItem : exportItems) {
        printWriter.println(
            joiner.join(
                quote(Formatter.date(exportItem.transaction().postDate)),
                quote(exportItem.account().name),
                quote(Formatter.currency(exportItem.split().amount()))));
      }
      printWriter.flush();
      printWriter.close();
    } catch (IOException e) {
      PromptEvaluator.showAndGetResult(
          TerminalProvider.get(),
          NoticePrompt.withMessages(
              ImmutableList.of(
                  "Failed to export model " + model,
                  "  to file: " + csvFile.getAbsolutePath(),
                  "  due to exception: " + e)));
    }
  }

  private static String quote(String s) {
    return "\"" + s.replace("\"", "") + "\"";
  }

  public interface ExportItem {
    Account account();

    Transaction transaction();

    Split split();
  }

  public interface ExportFilter {
    boolean shouldExclude(ExportItem exportItem);
  }
}
