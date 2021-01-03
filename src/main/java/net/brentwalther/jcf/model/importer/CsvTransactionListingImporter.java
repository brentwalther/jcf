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
import net.brentwalther.jcf.model.ModelGenerators;
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
    return Integer.parseInt(dollarValueString.replace(".", "").replace("$", ""));
  }

  public static JcfModelImporter create(JcfEnvironment jcfEnvironment) {
    ImmutableList<String> inputCsvLines = jcfEnvironment.getInputCsvLines();
    if (inputCsvLines.isEmpty()) {
      LOGGER.atSevere().log("CSV input lines are unexpectedly empty. Returning a no-op importer.");
      return NO_OP_IMPORTER;
    }
    ImmutableMap<DataField, Integer> csvFieldMappings = jcfEnvironment.getCsvFieldMappings();
    if (Sets.filter(
            ACCEPTABLE_DATA_FIELD_COMBINATIONS,
            (combination) -> Sets.difference(combination, csvFieldMappings.keySet()).isEmpty())
        .isEmpty()) {
      LOGGER.atSevere().log(
          "The input CSV field mappings are not sufficient. Returning a no-op importer. Found: [%s]. Wanted: [%s].",
          Joiner.on(", ").join(csvFieldMappings.keySet()),
          Joiner.on(" or ").join(ACCEPTABLE_DATA_FIELD_COMBINATIONS));
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

          private int lastDelimiter = -1;

          @Override
          protected String computeNext() {
            if (lastDelimiter >= csvString.length() - 1) {
              return endOfData();
            }
            int firstTokenCharIndex = lastDelimiter + 1;
            // If the next token is immediately another comma, don't go searching for the next one.
            // Just return the empty string.
            if (csvString.charAt(firstTokenCharIndex) == ',') {
              lastDelimiter = firstTokenCharIndex;
              return "";
            }
            // If the start of this token is a quote, we'll assume that this is supposed to be a
            // quoted column and we need to find the closing parentheses.
            if (csvString.charAt(firstTokenCharIndex) == '"') {
              // Add one to tokenStart to shave off the initial quote.
              int quoteStart = firstTokenCharIndex + 1;
              int quoteEnd = csvString.indexOf("\"", quoteStart);
              while (quoteEnd != -1 && csvString.charAt(quoteEnd - 1) == '\\') {
                quoteEnd = csvString.indexOf("\"", quoteEnd + 1);
              }
              if (quoteEnd > quoteStart) {
                lastDelimiter = quoteEnd + 1;
                return csvString.substring(quoteStart + 1, quoteEnd);
              }
            }
            // Otherwise just search for the next comma.
            int nextDelimiterIndex = csvString.indexOf(",", firstTokenCharIndex);
            if (nextDelimiterIndex == -1) {
              lastDelimiter = csvString.length();
              return csvString.substring(firstTokenCharIndex);
            } else {
              lastDelimiter = nextDelimiterIndex;
              return csvString.substring(firstTokenCharIndex, nextDelimiterIndex);
            }
          }
        });
  }

  @Override
  public Model get() {
    checkState(!csvLines.isEmpty());
    Iterable<String> rest = Iterables.skip(csvLines, 1);
    List<Transaction> transactions = new ArrayList<>();
    List<Split> splits = new ArrayList<>();
    int id = 0;
    for (String line : rest) {
      List<String> pieces = splitCsv(line);
      String dateString = getFieldValue(pieces, DataField.DATE, csvFieldPositions);
      if (dateString.isEmpty()) {
        LOGGER.atWarning().log(
            "Did not find a date at index %s in comma-delimited line. Skipping it: '%s'",
            csvFieldPositions.get(DataField.DATE), line);
        continue;
      }
      Instant date = Formatter.parseDateFrom(dateString, dateTimeFormatter);
      String description = getFieldValue(pieces, DataField.DESCRIPTION, csvFieldPositions);
      if (description.isEmpty()) {
        LOGGER.atWarning().log(
            "Declared description index %s was empty in comma-delimited line. Skipping it: '%s'",
            csvFieldPositions.get(DataField.DESCRIPTION), line);
        continue;
      }
      String amount = getFieldValue(pieces, DataField.AMOUNT, csvFieldPositions);
      String debit = getFieldValue(pieces, DataField.DEBIT, csvFieldPositions);
      String credit = getFieldValue(pieces, DataField.CREDIT, csvFieldPositions);
      int valueNumerator = 0;
      int valueDenominator = 100;
      if (!amount.isEmpty()) {
        valueNumerator = parseDollarValueAsCents(amount);
      } else if (!credit.isEmpty()) {
        valueNumerator = parseDollarValueAsCents(credit);
      } else {
        valueNumerator = -1 * parseDollarValueAsCents(debit);
      }
      Transaction transaction =
          Transaction.newBuilder()
              .setId(String.valueOf(id++))
              .setPostDateEpochSecond(date.getEpochSecond())
              .setDescription(description)
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
    return ModelGenerators.create(ImmutableList.of(fromAccount), transactions, splits);
  }

  private String getFieldValue(
      List<String> pieces, DataField field, Map<DataField, Integer> csvFieldPositions) {
    if (!csvFieldPositions.containsKey(field)) {
      return "";
    }
    int index = csvFieldPositions.get(field);
    if (index < 0 || index >= pieces.size()) {
      return "";
    }
    return pieces.get(index);
  }
}
