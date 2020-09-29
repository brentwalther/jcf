package net.brentwalther.jcf.model.importer;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import net.brentwalther.jcf.CsvMatcher;
import net.brentwalther.jcf.CsvMatcher.CsvField;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerator;
import net.brentwalther.jcf.util.Formatter;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class CsvTransactionListingImporter implements JcfModelImporter {

  private static final Splitter CSV_SPLITTER = Splitter.on(',');
  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private final File csvFile;
  private final Map<CsvMatcher.CsvField, Integer> csvFieldPositions;
  private final DateTimeFormatter dateTimeFormatter;
  private final Account fromAccount;

  private CsvTransactionListingImporter(
      File csvFile,
      Map<CsvField, Integer> csvFieldPositions,
      DateTimeFormatter dateTimeFormatter,
      Account fromAccount) {
    this.csvFile = csvFile;
    this.csvFieldPositions = csvFieldPositions;
    this.dateTimeFormatter = dateTimeFormatter;
    this.fromAccount = fromAccount;
  }

  public static CsvTransactionListingImporter create(
      File csvFile,
      Map<CsvMatcher.CsvField, Integer> csvFieldPositions,
      DateTimeFormatter dateTimeFormatter,
      Account fromAccount) {
    return new CsvTransactionListingImporter(
        csvFile, csvFieldPositions, dateTimeFormatter, fromAccount);
  }

  private static int parseDollarValueAsCents(String dollarValueString) {
    return Integer.parseInt(dollarValueString.replace(".", ""));
  }

  @Override
  public Model get() {
    int maxFieldPosition = csvFieldPositions.values().stream().max(Integer::compareTo).get();
    Scanner scanner = null;
    try {
      scanner = new Scanner(csvFile);
    } catch (FileNotFoundException e) {
      LOGGER.atSevere().withCause(e).log("Failed to open CSV file: %s", csvFile);
      return JcfModel.Model.getDefaultInstance();
    }
    int numFields = 0;
    if (!scanner.hasNextLine()) {
      System.err.println("The CSV file had no lines... Exiting.");
      System.exit(1);
    }
    numFields = Iterables.size(CSV_SPLITTER.split(scanner.nextLine()));
    if (numFields <= maxFieldPosition) {
      System.err.println(
          "The specified field positions ("
              + csvFieldPositions
              + " did not match the CSV format which had "
              + numFields
              + " fields. Exiting,");
      System.exit(1);
    }
    List<Transaction> transactions = new ArrayList<>();
    List<Split> splits = new ArrayList<>();
    int id = 0;
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      List<String> pieces = CSV_SPLITTER.splitToList(line);
      if (pieces.size() != numFields) {
        System.err.println(
            "Line had "
                + pieces.size()
                + " fields but expected "
                + numFields
                + ". Skipping it: "
                + line);
        continue;
      }
      Instant date =
          Formatter.parseDateFrom(
              pieces.get(csvFieldPositions.get(CsvField.DATE)), dateTimeFormatter);
      String desc = pieces.get(csvFieldPositions.get(CsvField.DESCRIPTION));
      int valueNumerator = 0;
      int valueDenominator = 100;
      if (csvFieldPositions.get(CsvField.AMOUNT) != -1) {
        valueNumerator =
            parseDollarValueAsCents(pieces.get(csvFieldPositions.get(CsvField.AMOUNT)));
      } else if (pieces.get(csvFieldPositions.get(CsvField.DEBIT)).isEmpty()) {
        valueNumerator =
            parseDollarValueAsCents(pieces.get(csvFieldPositions.get(CsvField.CREDIT)));
      } else {
        valueNumerator =
            -1 * parseDollarValueAsCents(pieces.get(csvFieldPositions.get(CsvField.DEBIT)));
      }
      Transaction transaction =
          Transaction.newBuilder()
              .setId(String.valueOf(id++))
              .setPostDateEpochSecond(date.getEpochSecond())
              .setDescription(desc)
              .build();
      transactions.add(transaction);
      splits.add(
          Split.newBuilder()
              .setAccountId(fromAccount.getId())
              .setTransactionId(transaction.getId())
              .setValueNumerator(valueNumerator)
              .setValueDenominator(valueDenominator)
              .build());
    }
    return ModelGenerator.create(ImmutableList.of(fromAccount), transactions, splits);
  }
}
