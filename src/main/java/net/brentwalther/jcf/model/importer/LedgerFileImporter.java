package net.brentwalther.jcf.model.importer;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Account.Type;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.model.ModelValidations;
import net.brentwalther.jcf.util.Formatter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LedgerFileImporter implements JcfModelImporter {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  private static final String ACCOUNT_NAME_PREFIX = "account";
  private static final Pattern CURRENCY_LIKE_AT_END_OF_LINE_PATTERN =
      Pattern.compile("[$][-]?[0-9,]+([.]\\d+)?\\s*$");
  private static final ImmutableMap<Pattern, DateTimeFormatter> DATE_TIME_PATTERNS_TO_FORMATTERS =
      ImmutableMap.<Pattern, DateTimeFormatter>builder()
          .put(
              Pattern.compile("[12]\\d{3}-\\d{2}-\\d{2}"),
              DateTimeFormatter.ofPattern("yyyy-MM-dd"))
          .put(
              Pattern.compile("[12]\\d{3}/\\d{2}/\\d{2}"),
              DateTimeFormatter.ofPattern("yyyy/MM/dd"))
          .build();
  private static final ImmutableMap<String, Type>
      ACCOUNT_TYPES_BY_LOWERCASE_TOP_LEVEL_ACCOUNT_NAMES =
          ImmutableMap.<String, Type>builder()
              .put("assets", Type.ASSET)
              .put("liabilities", Type.LIABILITY)
              .put("income", Type.INCOME)
              .put("expenses", Type.EXPENSE)
              .put("equity", Type.EQUITY)
              .build();

  private final ImmutableList<String> ledgerFileLines;

  private LedgerFileImporter(List<String> ledgerFileLines) {
    this.ledgerFileLines = ImmutableList.copyOf(ledgerFileLines);
  }

  public static LedgerFileImporter create(List<String> ledgerFileLines) {
    return new LedgerFileImporter(ledgerFileLines);
  }

  private static Account accountForLedgerName(String accountName) {
    return ModelGenerators.simpleAccount(accountName).toBuilder()
        .setType(
            guessAccountType(
                FluentIterable.from(Splitter.on(':').split(accountName)).first().or("")))
        .build();
  }

  private static Type guessAccountType(String topLevelAccountName) {
    if (Strings.isNullOrEmpty(topLevelAccountName)) {
      return Type.UNKNOWN_TYPE;
    }
    return ACCOUNT_TYPES_BY_LOWERCASE_TOP_LEVEL_ACCOUNT_NAMES.getOrDefault(
        topLevelAccountName.toLowerCase(), Type.UNKNOWN_TYPE);
  }

  public JcfModel.Model get() {
    Splitter spaceSplitter = Splitter.on(' ').omitEmptyStrings().trimResults();
    Joiner spaceJoiner = Joiner.on(' ');
    Map<String, Account> accountsById = new HashMap<>();
    Map<String, Transaction> transactionsById = new HashMap<>();
    Multimap<String, Split> splitsByTranscationId = ArrayListMultimap.create();

    Transaction currentTransaction = null;
    List<Split> currentSplits = new ArrayList<>();

    // Process all lines and ensure the last line is always an empty line.
    for (String line : FluentIterable.from(ledgerFileLines).append("")) {
      if (line.trim().isEmpty()) {
        // If the line is empty and we are currently processing a transaction, go ahead
        // and check that it and the splits are valid and then commit it.
        if (currentTransaction != null) {
          if (currentSplits.isEmpty()) {
            LOGGER.atWarning().log(
                "The transaction %s has no splits. Skipping it.",
                spaceJoiner.join(
                    Formatter.ledgerDate(
                        Instant.ofEpochSecond(currentTransaction.getPostDateEpochSecond())),
                    currentTransaction.getDescription()));
          }
          if (!ModelValidations.areSplitsBalanced(currentSplits)) {
            LOGGER.atWarning().log(
                "The transaction %s %s is not balanced! Splits are: [%s]",
                Formatter.ledgerDate(
                    Instant.ofEpochSecond(currentTransaction.getPostDateEpochSecond())),
                currentTransaction.getDescription(),
                Joiner.on(", ").join(currentSplits));
          }
          transactionsById.put(currentTransaction.getId(), currentTransaction);
          splitsByTranscationId.putAll(currentTransaction.getId(), currentSplits);
          currentTransaction = null;
          currentSplits.clear();
        }
        continue;
      }

      List<String> tokens = spaceSplitter.splitToList(line);

      // If we've got a non-null currentTransaction it means we're in the middle of extracting its
      // splits. Try to parse this line as one.
      if (currentTransaction != null) {
        Matcher currencyMatcher = CURRENCY_LIKE_AT_END_OF_LINE_PATTERN.matcher(line);
        boolean foundAmount = currencyMatcher.find();
        if (!foundAmount && currentSplits.isEmpty()) {
          LOGGER.atWarning().log(
              "Expected to but could not find a currency-like amount on line '%s'.\nSkipping it.",
              line);
          continue;
        }
        BigDecimal amount =
            foundAmount
                ? new BigDecimal(
                    // Create a big decimal from the regular base-10 number the matcher found,
                    // stripping formatting characters first.
                    line.substring(currencyMatcher.start(), currencyMatcher.end())
                        .trim()
                        .replace("$", "")
                        .replace(",", ""))
                : currentSplits.stream()
                    .map(ModelTransforms::bigDecimalAmountForSplit)
                    .reduce(BigDecimal.ZERO, (first, second) -> first.add(second))
                    .negate();
        Split.Builder splitBuilder =
            ModelGenerators.splitBuilderWithAmount(amount)
                .setTransactionId(currentTransaction.getId());
        Account account =
            accountForLedgerName(
                foundAmount ? line.substring(0, currencyMatcher.start()).trim() : line.trim());
        if (!accountsById.containsKey(account.getId())) {
          LOGGER.atInfo().log("Creating new account: %s", account);
          accountsById.put(account.getId(), account);
        }
        currentSplits.add(splitBuilder.setAccountId(account.getId()).build());
      } else if (tokens.get(0).equals(ACCOUNT_NAME_PREFIX)) {
        FluentIterable<String> rest = FluentIterable.from(tokens).skip(1);
        String accountName = spaceJoiner.join(rest);
        Account account = accountForLedgerName(accountName);
        if (!accountsById.containsKey(account.getId())) {
          LOGGER.atInfo().log("Adding new account from explicit account declaration: %s", account);
          accountsById.put(account.getId(), account);
        }
      } else if (isProbableDate(tokens.get(0))) {
        Optional<Instant> maybeInstant = parseDateAsInstant(tokens.get(0));
        if (!maybeInstant.isPresent()) {
          LOGGER.atWarning().log(
              "Thought this was a date but could not parse it: " + tokens.get(0));
          continue;
        }
        // For the first line of transaction, the format is defined at:
        // https://www.ledger-cli.org/3.0/doc/ledger3.html#index-transaction_002c-automated
        // It looks like: `DATE[=EDATE] [*|!] [(CODE)] DESC`
        Instant transactionInstant = maybeInstant.get();
        FluentIterable<String> restOfTokens = FluentIterable.from(tokens).skip(1);
        // If the next token is a * or ! it indicates the clear status. Go ahead and skip that
        // since we don't keep track of clearing status in our model.
        String maybeClearStatus = Iterables.getFirst(restOfTokens, "");
        if (maybeClearStatus.equals("*") || maybeClearStatus.equals("!")) {
          restOfTokens = restOfTokens.skip(1);
        }
        // We also don't keep track of the optional code in the current model so skip over it if
        // it's present.
        String maybeCode = Iterables.getFirst(restOfTokens, "");
        if (maybeCode.startsWith("(") && maybeCode.endsWith(")")) {
          restOfTokens = restOfTokens.skip(1);
        }
        String transactionDescription = spaceJoiner.join(restOfTokens);
        if (transactionDescription.isEmpty()) {
          LOGGER.atWarning().log(
              "Transaction occurring on date %s had no description!", tokens.get(0));
        }
        currentTransaction =
            Transaction.newBuilder()
                .setId(
                    Hashing.goodFastHash(32)
                        .newHasher()
                        .putInt(transactionInstant.hashCode())
                        .putInt(transactionDescription.hashCode())
                        .putDouble(Math.random())
                        .hash()
                        .toString())
                .setDescription(transactionDescription)
                .setPostDateEpochSecond(transactionInstant.getEpochSecond())
                .build();
      } else {
        LOGGER.atWarning().log("Ignoring line from ledger file: '%s'", line);
      }
    }
    LOGGER.atInfo().log(
        "Imported %s accounts, %s transactions, and %s splits from %s lines of a ledger CLI-compatible file.",
        accountsById.size(),
        transactionsById.size(),
        splitsByTranscationId.size(),
        ledgerFileLines.size());
    return ModelGenerators.create(
        accountsById.values(), transactionsById.values(), splitsByTranscationId.values());
  }

  private Optional<Instant> parseDateAsInstant(String s) {
    for (Pattern pattern : DATE_TIME_PATTERNS_TO_FORMATTERS.keySet()) {
      if (pattern.matches(s)) {
        return Optional.of(
            Formatter.parseDateFrom(s, DATE_TIME_PATTERNS_TO_FORMATTERS.get(pattern)));
      }
    }
    return Optional.absent();
  }

  private boolean isProbableDate(String s) {
    return FluentIterable.from(DATE_TIME_PATTERNS_TO_FORMATTERS.keySet())
        .anyMatch(p -> p.matches(s));
  }
}
