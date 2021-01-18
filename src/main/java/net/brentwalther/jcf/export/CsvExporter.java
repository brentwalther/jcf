package net.brentwalther.jcf.export;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.string.Formatter;

public class CsvExporter {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  public static void start(
      IndexedModel indexedModel, File csvFile, Iterable<ExportFilter> filters) {
    try {
      List<ExportItem> exportItems = new ArrayList<>();
      for (Split split : indexedModel.getAllSplits()) {
        ExportItem exportItem =
            ExportItem.create(
                indexedModel.getAccountById(split.getAccountId()),
                indexedModel.getTransactionById(split.getTransactionId()),
                split);
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
      LOGGER.atSevere().withCause(e).log(
          "Could not export CSV file to file: %s", csvFile.getAbsolutePath());
    }
  }

  private static String quote(String s) {
    return "\"" + s.replace("\"", "") + "\"";
  }

  public interface ExportFilter {
    boolean shouldExclude(ExportItem exportItem);
  }

  @AutoValue
  public abstract static class ExportItem {

    public static ExportItem create(Account account, Transaction transaction, Split split) {
      return new AutoValue_CsvExporter_ExportItem(account, transaction, split);
    }

    public abstract Account account();

    public abstract Transaction transaction();

    public abstract Split split();
  }
}
