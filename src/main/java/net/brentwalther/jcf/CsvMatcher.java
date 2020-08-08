package net.brentwalther.jcf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.FileType;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;
import net.brentwalther.jcf.model.importer.LedgerAccountListingImporter;
import net.brentwalther.jcf.model.importer.TsvTransactionDescAccountMappingImporter;
import net.brentwalther.jcf.screen.DateTimeFormatChooser;
import net.brentwalther.jcf.screen.FieldPositionChooser;
import net.brentwalther.jcf.screen.LedgerExportScreen;
import net.brentwalther.jcf.screen.SplitMatcherScreen;
import net.brentwalther.jcf.util.Formatter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CsvMatcher {

  @Parameter(
      names = {"--help", "-h"},
      help = true)
  private boolean help = false;

  @Parameter(
      names = {"--tsv_desc_account_mapping"},
      required = true)
  private String descToAccountTsvFileName = EMPTY;

  @Parameter(names = {"--ledger_account_listing"})
  private String ledgerAccountListingFileName = EMPTY;

  @Parameter(
      names = {"--transaction_csv"},
      required = true)
  private String csvFileName = EMPTY;

  @Parameter(names = {"--csv_field_ordering"})
  private String csvFieldOrdering = EMPTY;

  @Parameter(names = {"--date_format"})
  private String dateFormat = EMPTY;

  @Parameter(names = {"--account_name"})
  private String accountName = EMPTY;

  @Parameter(
      names = {"--output"},
      required = true)
  private String outputFileName = EMPTY;

  private static final String EMPTY = "";
  private static final String NEW_LINE = "\n";
  private static final ImmutableList<CsvField> CSV_FIELDS = ImmutableList.copyOf(CsvField.values());
  private static final ImmutableList<String> CSV_FIELD_NAMES =
      ImmutableList.copyOf(Lists.transform(CSV_FIELDS, (field) -> field.stringVal));
  private static final Splitter CSV_SPLITTER = Splitter.on(',');

  public static void main(String[] args) throws Exception {
    CsvMatcher csvMatcher = new CsvMatcher();
    JCommander.newBuilder().addObject(csvMatcher).build().parse(args);
    csvMatcher.run();
  }

  public void run() throws Exception {
    File mappingFile = new File(descToAccountTsvFileName);
    verifyFileOrDie(
        mappingFile,
        /* shouldExist= */ true,
        "The passed in TSV \"Transaction Desc\tAccount Name\\n\" file name \""
            + descToAccountTsvFileName
            + "\" does not refer to a file that exists.");
    Model model =
        extractModelFrom(mappingFile, FileType.TSV_TRANSACTION_DESCRIPTION_TO_ACCOUNT_NAME_MAPPING);

    if (!ledgerAccountListingFileName.isEmpty()) {
      File ledgerAccountListingFile = new File(ledgerAccountListingFileName);
      verifyFileOrDie(
          mappingFile,
          /* shouldExist= */ true,
          "The passed in ledger-format account listing file name \""
              + ledgerAccountListingFile
              + "\" does not refer to a file that exists.");
      Model allAccounts =
          extractModelFrom(ledgerAccountListingFile, FileType.LEDGER_ACCOUNT_LISTING);
      model = model.mergedWith(allAccounts);
    }
    SplitMatcher matcher = SplitMatcher.create(model);

    File csvFile = new File(csvFileName);
    verifyFileOrDie(
        csvFile,
        /* shouldExist= */ true,
        "The passed in transaction CSV file ("
            + csvFileName
            + ") does not refer to a file that exists.");
    ImmutableList<String> extractedFieldNames = ImmutableList.of();
    if (!csvFieldOrdering.isEmpty()) {
      List<String> inputFieldNames = CSV_SPLITTER.splitToList(csvFieldOrdering);
      // Ensure all fields are properly defined.
      if (Iterables.any(
          inputFieldNames, field -> !field.isEmpty() && !CSV_FIELD_NAMES.contains(field))) {
        System.err.println(
            new StringBuilder("")
                .append("The CSV column (field) ordering is not properly defined:" + NEW_LINE)
                .append(csvFieldOrdering + NEW_LINE)
                .append("It should be a csv string only containing fields: " + NEW_LINE)
                .append(Joiner.on(',').join(CSV_FIELD_NAMES) + NEW_LINE)
                .toString());
        System.exit(1);
      }
      extractedFieldNames = ImmutableList.copyOf(inputFieldNames);
    }
    final ImmutableList<String> finalExtractedFieldNames = extractedFieldNames;

    File ledgerFile = new File(outputFileName);
    if (ledgerFile.exists()) {
      System.err.println("The passed in output file (" + outputFileName + ") already exists!");
      System.exit(1);
    }

    ImmutableMap<CsvField, Integer> csvFieldPositions =
        extractedFieldNames.isEmpty()
            ? FieldPositionChooser.getPositionsFor(getFirstLineOf(csvFile))
            : Maps.toMap(CSV_FIELDS, (field) -> finalExtractedFieldNames.indexOf(field.stringVal));

    if (csvFieldPositions.isEmpty()) {
      System.err.println(
          "Unable to read CSV because there is no field mapping. Please either complete the mapping wizard or pass --csv_field_ordering");
      System.exit(1);
    }

    DateTimeFormatter dateTimeFormatter;
    if (dateFormat.isEmpty()) {
      dateTimeFormatter =
          DateTimeFormatChooser.obtainFormatForExamples(
              FluentIterable.from(loadAllLines(csvFile))
                  .skip(1)
                  .limit(10)
                  .transform(
                      (line) -> {
                        List<String> lineData =
                            Splitter.on(',').trimResults().omitEmptyStrings().splitToList(line);
                        if (!csvFieldPositions.containsKey(CsvField.DATE)
                            || csvFieldPositions.get(CsvField.DATE) >= lineData.size()) {
                          return null;
                        } else {
                          return lineData.get(csvFieldPositions.get(CsvField.DATE));
                        }
                      })
                  .filter(Predicates.notNull()));
    } else {
      dateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat);
    }

    if (dateTimeFormatter == null) {
      System.err.println(
          "Was not able to establish a date format for the dates in file: " + csvFileName);
      System.exit(1);
    }

    Account fromAccount =
        accountName == null || accountName.isEmpty()
            ? dummyAccount("An account")
            : dummyAccount(accountName);
    Model modelToMatch =
        createModelFromCsv(csvFile, csvFieldPositions, dateTimeFormatter, fromAccount);
    SplitMatcherScreen.start(matcher, modelToMatch, model.accountsById.values());

    if (!ledgerFile.createNewFile()) {
      System.err.println("Failed to create ledger output file.");
      System.exit(1);
    }
    LedgerExportScreen.start(
        Iterables.getOnlyElement(ModelManager.getUnmergedModels()),
        new FileOutputStream(ledgerFile));
  }

  private static ImmutableList<String> loadAllLines(File file) {
    try {
      return ImmutableList.copyOf(lineByLineIterator(file));
    } catch (IOException e) {
      System.err.println("IOException occured while loading file: " + file.getAbsolutePath());
      System.err.println(e.toString());
      System.exit(1);
    }
    return ImmutableList.of();
  }

  private static Iterable<String> lineByLineIterator(File csvFile) throws IOException {
    Scanner csvFileScanner = new Scanner(csvFile);
    return () ->
        new AbstractIterator<String>() {
          @Override
          protected String computeNext() {
            return csvFileScanner.hasNextLine() ? csvFileScanner.nextLine() : endOfData();
          }
        };
  }

  private String getFirstLineOf(File csvFile) throws FileNotFoundException {
    if (!csvFile.exists()) {
      throw new FileNotFoundException("Can't get first line of file: " + csvFile.getAbsolutePath());
    }
    return new Scanner(csvFile).nextLine();
  }

  private static Account dummyAccount(String accountName) {
    return new Account(accountName, accountName, JcfModel.Account.Type.ASSET, "");
  }

  private static Model extractModelFrom(File file, FileType fileType) {
    switch (fileType) {
      case TSV_TRANSACTION_DESCRIPTION_TO_ACCOUNT_NAME_MAPPING:
        return new TsvTransactionDescAccountMappingImporter().importFrom(loadAllLines(file));
      case LEDGER_ACCOUNT_LISTING:
        return new LedgerAccountListingImporter().importFrom(loadAllLines(file));
    }
    // If we don't have an importer for this file type, that's really not good. Just exit
    // immediately.
    System.err.println("Cannot handle file type: " + fileType.toString());
    System.exit(1);
    return Model.empty();
  }

  private static Model createModelFromCsv(
      File csvFile,
      ImmutableMap<CsvField, Integer> csvFieldPositions,
      DateTimeFormatter dateTimeFormatter,
      Account fromAccount)
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
      splits.add(new Split(fromAccount.id, transactionId, valueNum, valueDenom));
    }
    return new Model(ImmutableList.of(fromAccount), transactions, splits);
  }

  private static void verifyFileOrDie(File file, boolean shouldExist, String messageOnDeath) {
    if (!file.exists() || !file.isFile()) {
      System.err.println(messageOnDeath);
      System.err.println("Exiting.");
      System.exit(1);
    }
  }

  public enum CsvField {
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
