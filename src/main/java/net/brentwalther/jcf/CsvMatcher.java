package net.brentwalther.jcf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;
import net.brentwalther.jcf.screen.LedgerExportScreen;
import net.brentwalther.jcf.screen.SplitMatcherScreen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class CsvMatcher {

  @Parameter(
      names = {"--help", "-h"},
      help = true)
  private boolean help;

  @Parameter(names = {"--mapping_file"})
  private String mappingFileName;

  @Parameter(names = {"--transaction_csv"})
  private String csvFileName;

  @Parameter(names = {"--csv_field_ordering"})
  private String csvFieldOrdering;

  @Parameter(names = {"--date_format"})
  private String dateFormat;

  @Parameter(names = {"--output"})
  private String outputFileName;

  private static final ImmutableSet<CsvField> CSV_FIELDS = ImmutableSet.copyOf(CsvField.values());
  private static final Splitter CSV_SPLITTER = Splitter.on(',');
  private static final Splitter TSV_SPLITTER = Splitter.on('\t');

  public static void main(String[] args) throws Exception {
    CsvMatcher csvMatcher = new CsvMatcher();
    JCommander.newBuilder().addObject(csvMatcher).build().parse(args);
    csvMatcher.run();
  }

  public void run() throws Exception {
    File mappingFile = new File(mappingFileName);
    if (!mappingFile.exists() || !mappingFile.isFile()) {
      System.err.println(
          "The passed in mapping file name ("
              + mappingFileName
              + ") does not refer to a file that exists.");
      System.exit(1);
    }
    File csvFile = new File(csvFileName);
    if (!csvFile.exists() || !csvFile.isFile()) {
      System.err.println(
          "The passed in transaction CSV file ("
              + csvFileName
              + ") does not refer to a file that exists.");
      System.exit(1);
    }
    List<String> fieldPositions = CSV_SPLITTER.splitToList(csvFieldOrdering);
    Set<String> csvFieldNames =
        ImmutableSet.copyOf(Iterables.transform(CSV_FIELDS, (field) -> field.stringVal));
    if (Iterables.any(
        fieldPositions, field -> !field.isEmpty() && !csvFieldNames.contains(field))) {
      System.err.println(
          "The field ordering ("
              + csvFieldOrdering
              + ") must be a csv string only containing fields: "
              + csvFieldNames);
      System.exit(1);
    }

    File ledgerFile = new File(outputFileName);
    if (ledgerFile.exists()) {
      System.err.println("The passed in output file (" + outputFileName + ") already exists!");
      System.exit(1);
    }

    Model mappingsModel = extractModelFrom(mappingFile);
    SplitMatcher matcher = new SplitMatcher(mappingsModel);
    Model model =
        createModelFromCsv(
            csvFile,
            Maps.toMap(CSV_FIELDS, (field) -> fieldPositions.indexOf(field.stringVal)),
            dateFormat);
    SplitMatcherScreen.start(matcher, model, mappingsModel.accountsById.values());

    if (!ledgerFile.createNewFile()) {
      System.err.println("Failed to create ledger output file.");
      System.exit(1);
    }
    LedgerExportScreen.start(
        Iterables.getOnlyElement(ModelManager.getUnmergedModels()),
        new FileOutputStream(ledgerFile));
  }

  private static Model extractModelFrom(File mappingFile) throws FileNotFoundException {
    Scanner scanner = new Scanner(mappingFile);
    Map<String, Account> accountsById = new HashMap<>();
    List<Transaction> transactions = new ArrayList<>();
    List<Split> splits = new ArrayList<>();
    int id = 0;
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      List<String> pieces = TSV_SPLITTER.splitToList(line);
      if (pieces.size() != 2) {
        System.err.println(
            "Mappings file has a bad line. Skipping it. Expected a 2-value TSV line but saw: "
                + line);
        continue;
      }
      String payee = pieces.get(0);
      String account = pieces.get(1);
      String transactionId = String.valueOf(id++);

      accountsById.putIfAbsent(account, new Account(account, account, Account.Type.EXPENSE, ""));
      transactions.add(
          new Transaction(
              Transaction.DataSource.EXPENSE_MAPPING_FILE, transactionId, Instant.now(), payee));
      splits.add(new Split(account, transactionId, 0, 1));
    }
    return new Model(accountsById.values(), transactions, splits);
  }

  private static Model createModelFromCsv(
      File csvFile, ImmutableMap<CsvField, Integer> csvFieldPositions, String dateFormat)
      throws FileNotFoundException {
    int maxFieldPosition = csvFieldPositions.values().stream().max(Integer::compareTo).get();
    Scanner scanner = new Scanner(csvFile);
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
    Account placeholderAccount = new Account("placeholder", "An Account", Account.Type.ASSET, "");
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
          LocalDate.from(
                  DateTimeFormatter.ofPattern(dateFormat)
                      .parse(pieces.get(csvFieldPositions.get(CsvField.DATE))))
              .atStartOfDay(ZoneId.systemDefault())
              .toInstant();
      String desc = pieces.get(csvFieldPositions.get(CsvField.DESCRIPTION));
      int valueNum = 0;
      int valueDenom = 100;
      if (csvFieldPositions.get(CsvField.AMOUNT) != -1) {
        valueNum =
            Integer.valueOf(pieces.get(csvFieldPositions.get(CsvField.AMOUNT)).replace(".", ""));
      } else if (pieces.get(csvFieldPositions.get(CsvField.DEBIT)).isEmpty()) {
        valueNum =
            Integer.valueOf(pieces.get(csvFieldPositions.get(CsvField.CREDIT)).replace(".", ""));
      } else {
        valueNum =
            Integer.valueOf(pieces.get(csvFieldPositions.get(CsvField.DEBIT)).replace(".", ""))
                * -1;
      }
      String transactionId = String.valueOf(id++);
      transactions.add(new Transaction(Transaction.DataSource.CSV, transactionId, date, desc));
      splits.add(new Split(placeholderAccount.id, transactionId, valueNum, valueDenom));
    }
    return new Model(ImmutableList.of(placeholderAccount), transactions, splits);
  }

  private enum CsvField {
    DATE("date"),
    DESCRIPTION("desc"),
    DEBIT("debit"),
    CREDIT("credit"),
    AMOUNT("amt");

    private final String stringVal;

    CsvField(String stringVal) {
      this.stringVal = stringVal;
    }
  }
}
