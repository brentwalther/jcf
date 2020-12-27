package net.brentwalther.jcf.model.importer;

import com.google.common.base.Joiner;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import net.brentwalther.jcf.JcfEnvironment;
import net.brentwalther.jcf.SettingsProto.SettingsProfile.DataField;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerator;
import net.brentwalther.jcf.util.Formatter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

public class CsvTransactionListingImporter implements JcfModelImporter {

  private static final JcfModelImporter NO_OP_IMPORTER = () -> Model.getDefaultInstance();

  private static final ImmutableSet<ImmutableSet<DataField>> ACCEPTABLE_DATA_FIELD_COMBINATIONS =
      ImmutableSet.of(
          Sets.immutableEnumSet(DataField.DATE, DataField.DESCRIPTION, DataField.AMOUNT),
          Sets.immutableEnumSet(
              DataField.DATE, DataField.DESCRIPTION, DataField.CREDIT, DataField.DEBIT));

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  private final ImmutableList<String> csvLines;
  private final Map<DataField, Integer> csvFieldPositions;
  private final DateTimeFormatter dateTimeFormatter;
  private final Account fromAccount;

  private CsvTransactionListingImporter(
      ImmutableList<String> csvLines,
      Map<DataField, Integer> csvFieldPositions,
      DateTimeFormatter dateTimeFormatter,
      Account fromAccount) {
    this.csvLines = csvLines;
    this.csvFieldPositions = csvFieldPositions;
    this.dateTimeFormatter = dateTimeFormatter;
    this.fromAccount = fromAccount;
  }

  private static int parseDollarValueAsCents(String dollarValueString) {
    return Integer.parseInt(dollarValueString.replace(".", ""));
  }

  public static JcfModelImporter create(JcfEnvironment jcfEnvironment) {
    ImmutableList<String> inputCsvLines = jcfEnvironment.getInputCsvLines();
    ImmutableMap<DataField, Integer> csvFieldMappings = jcfEnvironment.getCsvFieldMappings();
    if (inputCsvLines.isEmpty()) {
      LOGGER.atSevere().log("CSV input lines are unexpectedly empty. Returning a no-op importer.");
      return NO_OP_IMPORTER;
    }
    if (Sets.filter(
            ACCEPTABLE_DATA_FIELD_COMBINATIONS,
            (combination) -> Sets.difference(combination, csvFieldMappings.keySet()).isEmpty())
        .isEmpty()) {
      LOGGER.atSevere().log(
          "The input CSV field mappings are not sufficient. Returning a no-op importer. Found: [%s]. Wanted: [%s].",
          Joiner.on(", ").join(csvFieldMappings.keySet()),
          Joiner.on(", ").join(ACCEPTABLE_DATA_FIELD_COMBINATIONS));
      return NO_OP_IMPORTER;
    }

    Optional<DateTimeFormatter> csvDateTimeFormatter = jcfEnvironment.getCsvDateFormat();
    if (!csvDateTimeFormatter.isPresent()) {
      LOGGER.atSevere().log("No declared CSV date format. Returning a no-op importer.");
      return NO_OP_IMPORTER;
    }

    Optional<Account> csvAccount = jcfEnvironment.getCsvAccount();
    if (!csvAccount.isPresent()) {
      LOGGER.atSevere().log("No declared CSV account. Returning a no-op importer.");
      return NO_OP_IMPORTER;
    }

    return new CsvTransactionListingImporter(
        inputCsvLines, csvFieldMappings, csvDateTimeFormatter.get(), csvAccount.get());
  }

  private static ImmutableList<String> splitCsv(String csvString) {
    return ImmutableList.copyOf(
        new AbstractIterator<String>() {

          private int lastTokenEnd = -1;

          @Override
          protected String computeNext() {
            String nextToken = "";
            while (nextToken.isEmpty()) {
              if (lastTokenEnd == csvString.length()) {
                return endOfData();
              }
              int tokenStart = lastTokenEnd + 1;
              int nextTokenEnd = csvString.indexOf(",", tokenStart);
              if (nextTokenEnd == -1) {
                nextTokenEnd = csvString.length();
              }
              // If the start of this token is a quote, disregard any comments if we're able to find
              // matching close quotations.
              if (tokenStart < csvString.length() && csvString.charAt(tokenStart) == '"') {
                // Add one to tokenStart to shave off the initial quote.
                int quoteStart = tokenStart + 1;
                int quoteEnd = csvString.indexOf("\"", quoteStart);
                if (quoteEnd > quoteStart) {
                  tokenStart = quoteStart;
                  nextTokenEnd = quoteEnd;
                }
              }
              nextToken = csvString.substring(tokenStart, nextTokenEnd);
              lastTokenEnd = nextTokenEnd;
            }
            return nextToken;
          }
        });
  }

  @Override
  public Model get() {
    checkState(!csvLines.isEmpty());
    List<String> columnHeaderNames = splitCsv(csvLines.get(0));
    Iterable<String> rest = Iterables.skip(csvLines, 1);
    List<Transaction> transactions = new ArrayList<>();
    List<Split> splits = new ArrayList<>();
    int id = 0;
    for (String line : rest) {
      List<String> pieces = splitCsv(line);
      if (pieces.size() != columnHeaderNames.size()) {
        // TODO: This is buggy because it skips lines that simply had an extra comma in them (lax
        //   CSV format). We should as least try to parse it and see what happens.
        LOGGER.atWarning().log(
            "Line had %s comma-delimited fields but expected %s. Skipping it: '%s'",
            pieces.size(), columnHeaderNames.size(), line);
        continue;
      }
      Instant date =
          Formatter.parseDateFrom(
              pieces.get(csvFieldPositions.get(DataField.DATE)), dateTimeFormatter);
      String desc = pieces.get(csvFieldPositions.get(DataField.DESCRIPTION));
      int valueNumerator = 0;
      int valueDenominator = 100;
      if (csvFieldPositions.get(DataField.AMOUNT) != -1) {
        valueNumerator =
            parseDollarValueAsCents(pieces.get(csvFieldPositions.get(DataField.AMOUNT)));
      } else if (pieces.get(csvFieldPositions.get(DataField.DEBIT)).isEmpty()) {
        valueNumerator =
            parseDollarValueAsCents(pieces.get(csvFieldPositions.get(DataField.CREDIT)));
      } else {
        valueNumerator =
            -1 * parseDollarValueAsCents(pieces.get(csvFieldPositions.get(DataField.DEBIT)));
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
