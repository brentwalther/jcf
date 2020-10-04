package net.brentwalther.jcf.screen;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.util.Formatter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CsvExportScreen {

  public static void start(
      IndexedModel indexedModel, File csvFile, Iterable<ExportFilter> filters) {
    try {
      List<ExportItem> exportItems = new ArrayList<>();
      for (Split split : indexedModel.getAllSplits()) {
        ExportItem exportItem =
            new ExportItem() {
              @Override
              public Account account() {
                return indexedModel.getAccountById(split.getAccountId());
              }

              @Override
              public Transaction transaction() {
                return indexedModel.getTransactionById(split.getTransactionId());
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
          Comparator.comparingLong(
              (exportItem) -> exportItem.transaction().getPostDateEpochSecond()));

      PrintWriter printWriter = new PrintWriter(csvFile);
      Joiner joiner = Joiner.on(",");
      for (ExportItem exportItem : exportItems) {
        printWriter.println(
            joiner.join(
                quote(
                    Formatter.date(
                        Instant.ofEpochSecond(exportItem.transaction().getPostDateEpochSecond()))),
                quote(exportItem.account().getName()),
                quote(
                    Formatter.currency(
                        ModelTransforms.bigDecimalAmountForSplit(exportItem.split())))));
      }
      printWriter.flush();
      printWriter.close();
    } catch (IOException e) {
      PromptEvaluator.showAndGetResult(
          TerminalProvider.get(),
          NoticePrompt.withMessages(
              ImmutableList.of(
                  "Failed to export model " + indexedModel,
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
