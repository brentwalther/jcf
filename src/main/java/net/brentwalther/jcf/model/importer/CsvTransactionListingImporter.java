package net.brentwalther.jcf.model.importer;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.re2j.Pattern;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.brentwalther.jcf.SettingsProto.SettingsProfile.DataField;
import net.brentwalther.jcf.environment.JcfEnvironment;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.string.Formatter;

public class CsvTransactionListingImporter implements JcfModelImporter {

  public static final Function<String, ImmutableList<String>> CSV_SPLITTER =
      csvString ->
          ImmutableList.copyOf(
              new AbstractIterator<String>() {

                private int lastDelimiter = -1;

                @Override
                protected String computeNext() {
                  if (lastDelimiter >= csvString.length() - 1) {
                    return endOfData();
                  }
                  int firstTokenCharIndex = lastDelimiter + 1;
                  // If the next token is immediately another comma, don't go searching for the next
                  // one.
                  // Just return the empty string.
                  if (csvString.charAt(firstTokenCharIndex) == ',') {
                    lastDelimiter = firstTokenCharIndex;
                    return "";
                  }
                  // If the start of this token is a quote, we'll assume that this is supposed to be
                  // a
                  // quoted column and we need to find the closing parentheses.
                  if (csvString.charAt(firstTokenCharIndex) == '"') {
                    // Add one to tokenStart to shave off the initial quote.
                    int quoteStart = firstTokenCharIndex + 1;
                    int quoteEnd = csvString.indexOf('"', quoteStart);
                    while (quoteEnd != -1 && csvString.charAt(quoteEnd - 1) == '\\') {
                      quoteEnd = csvString.indexOf('"', quoteEnd + 2);
                    }
                    if (quoteEnd > quoteStart) {
                      lastDelimiter = quoteEnd + 1;
                      return csvString.substring(quoteStart, quoteEnd);
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
  private static final JcfModelImporter NO_OP_IMPORTER = () -> Model.getDefaultInstance();
  private static final ImmutableSet<ImmutableSet<DataField>> ACCEPTABLE_DATA_FIELD_COMBINATIONS =
      ImmutableSet.of(
          Sets.immutableEnumSet(DataField.DATE, DataField.DESCRIPTION, DataField.AMOUNT),
          Sets.immutableEnumSet(DataField.DATE, DataField.DESCRIPTION, DataField.NEGATED_AMOUNT),
          Sets.immutableEnumSet(
              DataField.DATE, DataField.DESCRIPTION, DataField.CREDIT, DataField.DEBIT));
  private static final Pattern NON_DECIMAL_CHARACTERS_PATTERN = Pattern.compile("[^\\-0-9.]");
  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private final ImmutableList<String> csvLines;
  private final Map<DataField, Integer> csvFieldPositions;
  private final DateTimeFormatter dateTimeFormatter;
  private final Function<String, Account> accountGenerator;

  private CsvTransactionListingImporter(
      ImmutableList<String> csvLines,
      Map<DataField, Integer> csvFieldPositions,
      DateTimeFormatter dateTimeFormatter,
      Function<String, Account> accountGenerator) {
    this.csvLines = csvLines;
    this.csvFieldPositions = csvFieldPositions;
    this.dateTimeFormatter = dateTimeFormatter;
    this.accountGenerator = accountGenerator;
  }

  private static int parseCurrencyValueStringAsCents(String dollarValueString) {
    String strippedString =
        NON_DECIMAL_CHARACTERS_PATTERN.matcher(dollarValueString).replaceAll("");
    checkState(
        strippedString.indexOf('.') == strippedString.lastIndexOf('.'),
        "Decimal-like value '%s' had more than one decimal point!",
        strippedString);
    checkState(
        strippedString.indexOf('-') == strippedString.lastIndexOf('-'),
        "Decimal-like value '%s' had more than one negative sign!",
        strippedString);
    int decimalPointIndex = strippedString.indexOf('.');
    if (decimalPointIndex < 0) {
      return Integer.parseInt(strippedString) * 100;
    } else if (decimalPointIndex == dollarValueString.length() - 1) {
      return Integer.parseInt(strippedString.substring(0, decimalPointIndex)) * 100;
    } else {
      boolean isNegative = strippedString.charAt(0) == '-';
      int dollars =
          Integer.parseInt(strippedString.substring(isNegative ? 1 : 0, decimalPointIndex));
      int fraction = Integer.parseInt(strippedString.substring(decimalPointIndex + 1));
      while (fraction >= 100) {
        fraction /= 10;
      }
      return (dollars * 100 + fraction) * (isNegative ? -1 : 1);
    }
  }

  public static JcfModelImporter create(JcfEnvironment jcfEnvironment) {
    ImmutableList<String> inputCsvLines = jcfEnvironment.getInputCsvLines();
    if (inputCsvLines.isEmpty()) {
      LOGGER.atSevere().log("CSV input lines are unexpectedly empty. Returning a no-op importer.");
      return NO_OP_IMPORTER;
    }
    ImmutableMap<DataField, Integer> csvFieldMappings = jcfEnvironment.getCsvFieldMappings();
    if (!isAcceptableFieldMappingSet(csvFieldMappings.keySet())) {
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

    return new CsvTransactionListingImporter(
        inputCsvLines,
        csvFieldMappings,
        csvDateTimeFormatter.get(),
        jcfEnvironment.getImportAccountGenerator());
  }

  /** Returns true if the set of data fields is sufficient for attempting to do an import. */
  public static boolean isAcceptableFieldMappingSet(ImmutableSet<DataField> fields) {
    return !Sets.filter(
            ACCEPTABLE_DATA_FIELD_COMBINATIONS,
            (combination) -> Sets.difference(combination, fields).isEmpty())
        .isEmpty();
  }

  @Override
  public Model get() {
    checkState(!csvLines.isEmpty());
    ImmutableList.Builder<Transaction> transactions =
        ImmutableList.builderWithExpectedSize(csvLines.size() - 1);
    ImmutableList.Builder<Split> splits = ImmutableList.builder();
    ImmutableSet.Builder<Account> allAccounts = ImmutableSet.builder();
    Supplier<Hasher> idHasherSupplier =
        () ->
            Hashing.goodFastHash(32)
                .newHasher()
                .putLong(System.currentTimeMillis())
                .putDouble(Math.random());
    for (int i = 1; i < csvLines.size(); i++) {
      String line = csvLines.get(i);
      if (line.isEmpty()) {
        // An empty line isn't interesting. Just ignore it rather than logging
        // a warning message.
        continue;
      }
      List<String> pieces = CSV_SPLITTER.apply(line);
      String dateString = getFieldValue(pieces, DataField.DATE, csvFieldPositions);
      if (dateString.isEmpty()) {
        LOGGER.atWarning().log(
            "Didn't find a date at row %s, column index %s of CSV file.\nLine - %s",
            i, csvFieldPositions.get(DataField.DATE), line);
        continue;
      }
      Instant date = Formatter.parseDateFrom(dateString, dateTimeFormatter);
      String description = getFieldValue(pieces, DataField.DESCRIPTION, csvFieldPositions);
      if (description.isEmpty()) {
        LOGGER.atWarning().log(
            "Didn't find a description at row %s, column index %s of CSV line.\nLine - %s",
            csvFieldPositions.get(DataField.DESCRIPTION), line);
        continue;
      }
      String amount = getFieldValue(pieces, DataField.AMOUNT, csvFieldPositions);
      String negatedAmount = getFieldValue(pieces, DataField.NEGATED_AMOUNT, csvFieldPositions);
      String debit = getFieldValue(pieces, DataField.DEBIT, csvFieldPositions);
      String credit = getFieldValue(pieces, DataField.CREDIT, csvFieldPositions);
      int valueNumerator = 0;
      int valueDenominator = 100;
      if (!amount.isEmpty()) {
        valueNumerator = parseCurrencyValueStringAsCents(amount);
      } else if (!negatedAmount.isEmpty()) {
        valueNumerator = -1 * parseCurrencyValueStringAsCents(negatedAmount);
      } else if (!credit.isEmpty()) {
        valueNumerator = parseCurrencyValueStringAsCents(credit);
      } else {
        valueNumerator = -1 * parseCurrencyValueStringAsCents(debit);
      }
      Account fromAccount =
          accountGenerator.apply(
              csvFieldPositions.containsKey(DataField.ACCOUNT_IDENTIFIER)
                  ? pieces.get(csvFieldPositions.get(DataField.ACCOUNT_IDENTIFIER))
                  : "?");
      allAccounts.add(fromAccount);
      Transaction transaction =
          Transaction.newBuilder()
              .setId(idHasherSupplier.get().putInt(i).hash().toString())
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
    return ModelGenerators.create(
        allAccounts.build().asList(), transactions.build(), splits.build());
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
