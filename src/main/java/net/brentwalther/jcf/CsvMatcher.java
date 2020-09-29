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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.FileType;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.ModelGenerator;
import net.brentwalther.jcf.model.importer.CsvTransactionListingImporter;
import net.brentwalther.jcf.model.importer.LedgerAccountListingImporter;
import net.brentwalther.jcf.model.importer.TsvTransactionDescAccountMappingImporter;
import net.brentwalther.jcf.prompt.AccountPickerPrompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.screen.DateTimeFormatChooser;
import net.brentwalther.jcf.screen.FieldPositionChooser;
import net.brentwalther.jcf.screen.LedgerExportScreen;
import net.brentwalther.jcf.screen.SplitMatcherScreen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

public class CsvMatcher {

  /** The empty string, representing an unset string flag value. */
  private static final String UNSET = "";

  private static final String NEW_LINE = "\n";
  private static final ImmutableList<CsvField> CSV_FIELDS = ImmutableList.copyOf(CsvField.values());
  private static final ImmutableList<String> CSV_FIELD_NAMES =
      ImmutableList.copyOf(Lists.transform(CSV_FIELDS, (field) -> field.stringVal));
  private static final Splitter CSV_SPLITTER = Splitter.on(',');

  @Parameter(
      names = {"--help", "-h"},
      help = true)
  private boolean help = false;

  @Parameter(
      names = {"--tsv_desc_account_mapping"},
      required = true)
  private String descToAccountTsvFileName = UNSET;

  @Parameter(names = {"--ledger_account_listing"})
  private String ledgerAccountListingFileName = UNSET;

  @Parameter(
      names = {"--transaction_csv"},
      required = true)
  private String csvFileName = UNSET;

  @Parameter(names = {"--csv_field_ordering"})
  private String csvFieldOrdering = UNSET;

  @Parameter(names = {"--date_format"})
  private String dateFormat = UNSET;

  @Parameter(names = {"--account_name"})
  private String accountName = UNSET;

  @Parameter(
      names = {"--output"},
      required = true)
  private String outputFileName = UNSET;

  public static void main(String[] args) throws Exception {
    CsvMatcher csvMatcher = new CsvMatcher();
    JCommander.newBuilder().addObject(csvMatcher).build().parse(args);
    csvMatcher.run();
  }

  private static ImmutableList<String> loadAllLines(File file) {
    try {
      return ImmutableList.copyOf(lineByLineIterator(file));
    } catch (IOException e) {
      System.err.println("IOException occurred while loading file: " + file.getAbsolutePath());
      System.err.println(e.toString());
      System.exit(1);
    }
    return ImmutableList.of();
  }

  private static Iterator<String> lineByLineIterator(File csvFile) throws IOException {
    Scanner csvFileScanner = new Scanner(csvFile);
    return new AbstractIterator<String>() {
      @Override
      protected String computeNext() {
        return csvFileScanner.hasNextLine() ? csvFileScanner.nextLine() : endOfData();
      }
    };
  }

  private static Account dummyAccount(String accountName) {
    return Account.newBuilder()
        .setId(accountName)
        .setName(accountName)
        .setType(Account.Type.UNKNOWN_TYPE)
        .build();
  }

  private static JcfModel.Model extractModelFrom(File file, FileType fileType) {
    switch (fileType) {
      case TSV_TRANSACTION_DESCRIPTION_TO_ACCOUNT_NAME_MAPPING:
        return TsvTransactionDescAccountMappingImporter.create(loadAllLines(file)).get();
      case LEDGER_ACCOUNT_LISTING:
        return LedgerAccountListingImporter.create(loadAllLines(file)).get();
    }
    // If we don't have an importer for this file type, that's really not good. Just exit
    // immediately.
    System.err.println("Cannot handle file type: " + fileType.toString());
    System.exit(1);
    return ModelGenerator.empty();
  }

  private static void verifyFileExistsOrDie(File file, String messageOnDeath) {
    if (!file.exists() || !file.isFile()) {
      System.err.println(messageOnDeath);
      System.err.println("Exiting.");
      System.exit(1);
    }
  }

  private void run() throws Exception {
    if (help) {
      StringBuilder usageStringBuilder = new StringBuilder();
      JCommander.newBuilder().addObject(this).build().getUsageFormatter().usage(usageStringBuilder);
      System.err.print(usageStringBuilder);
      System.exit(1);
    }
    File ledgerFile = new File(outputFileName);
    if (ledgerFile.exists()) {
      System.err.println("The passed in output file (" + outputFileName + ") already exists!");
      System.exit(1);
    }

    File mappingFile = new File(descToAccountTsvFileName);
    verifyFileExistsOrDie(
        mappingFile,
        "The passed in TSV \"Transaction Desc\tAccount Name\\n\" file name \""
            + descToAccountTsvFileName
            + "\" does not refer to a file that exists.");
    JcfModel.Model matchModel =
        extractModelFrom(mappingFile, FileType.TSV_TRANSACTION_DESCRIPTION_TO_ACCOUNT_NAME_MAPPING);

    if (!ledgerAccountListingFileName.isEmpty()) {
      File ledgerAccountListingFile = new File(ledgerAccountListingFileName);
      verifyFileExistsOrDie(
          mappingFile,
          "The passed in ledger-format account listing file name \""
              + ledgerAccountListingFile
              + "\" does not refer to a file that exists.");
      JcfModel.Model allAccounts =
          extractModelFrom(ledgerAccountListingFile, FileType.LEDGER_ACCOUNT_LISTING);
      matchModel = ModelGenerator.merge(allAccounts, matchModel);
    }
    SplitMatcher matcher = SplitMatcher.create(matchModel);

    File csvFile = new File(csvFileName);
    verifyFileExistsOrDie(
        csvFile,
        "The passed in transaction CSV file ("
            + csvFileName
            + ") does not refer to a file that exists.");
    ImmutableMap<CsvField, Integer> csvFieldPositions = ImmutableMap.of();
    if (!csvFieldOrdering.isEmpty()) {
      List<String> inputFieldNames = CSV_SPLITTER.splitToList(csvFieldOrdering);
      // Ensure all fields are properly defined.
      if (inputFieldNames.stream()
          .anyMatch(
              field -> field != null && !field.isEmpty() && !CSV_FIELD_NAMES.contains(field))) {
        System.err.println(
            "The CSV column (field) ordering is not properly defined:"
                + NEW_LINE
                + csvFieldOrdering
                + NEW_LINE
                + "It should be a csv string only containing fields: "
                + NEW_LINE
                + Joiner.on(',').join(CSV_FIELD_NAMES)
                + NEW_LINE);
        System.exit(1);
      }
      csvFieldPositions =
          ImmutableMap.copyOf(
              FluentIterable.from(CSV_FIELDS)
                  .transform(
                      field -> Maps.immutableEntry(field, inputFieldNames.indexOf(field.stringVal)))
                  .filter(entry -> entry.getValue() > -1));
    }

    if (csvFieldPositions.isEmpty()) {
      csvFieldPositions = FieldPositionChooser.getPositionsFor(getFirstLineOf(csvFile));

      if (csvFieldPositions.isEmpty()) {
        System.err.println(
            "Unable to read CSV because there is no field mapping. Please either complete the mapping wizard or pass --csv_field_ordering");
        System.exit(1);
      }
    }

    DateTimeFormatter dateTimeFormatter;
    if (dateFormat.isEmpty()) {
      final ImmutableMap<CsvField, Integer> finalCsvFieldPositions = csvFieldPositions;
      dateTimeFormatter =
          DateTimeFormatChooser.obtainFormatForExamples(
              FluentIterable.from(loadAllLines(csvFile))
                  .skip(1)
                  .limit(10)
                  .transform(
                      (line) -> {
                        List<String> fields =
                            Splitter.on(',').trimResults().omitEmptyStrings().splitToList(line);
                        if (!finalCsvFieldPositions.containsKey(CsvField.DATE)
                            || finalCsvFieldPositions.get(CsvField.DATE) >= fields.size()
                            || finalCsvFieldPositions.get(CsvField.DATE) < 0) {
                          return null;
                        } else {
                          return fields.get(finalCsvFieldPositions.get(CsvField.DATE));
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

    if (!ledgerFile.createNewFile()) {
      System.err.println("Failed to create ledger output file.");
      System.exit(1);
    }

    Account fromAccount =
        accountName.equals(UNSET)
            ? PromptEvaluator.showAndGetResult(
                TerminalProvider.get(),
                PromptDecorator.decorateWithStatusBars(
                    AccountPickerPrompt.create(matchModel.getAccountList()),
                    ImmutableList.of("Please choose the account this CSV file represents.")))
            : dummyAccount(accountName);

    if (fromAccount == null) {
      System.err.println("Could not determine an account for the import.");
      System.exit(1);
    }

    Model model =
        CsvTransactionListingImporter.create(
                csvFile, csvFieldPositions, dateTimeFormatter, fromAccount)
            .get();
    Model modelToExport =
        SplitMatcherScreen.start(matcher, IndexedModel.create(model), matchModel.getAccountList());
    LedgerExportScreen.start(IndexedModel.create(modelToExport), new FileOutputStream(ledgerFile));
  }

  private String getFirstLineOf(File csvFile) throws FileNotFoundException {
    if (!csvFile.exists()) {
      throw new FileNotFoundException("Can't get first line of file: " + csvFile.getAbsolutePath());
    }
    return new Scanner(csvFile).nextLine();
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
