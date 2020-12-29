package net.brentwalther.jcf.model.importer;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
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
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerator;
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
import java.util.function.Function;

public class LedgerFileImporter implements JcfModelImporter {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  private static final String ACCOUNT_NAME_PREFIX = "account";
  private static final Pattern USD_CURRENCY_PATTERN = Pattern.compile("[$]-?[0-9,]+([.]\\d+)?");
  private static final ImmutableMap<Pattern, DateTimeFormatter> DATE_TIME_PATTERNS_TO_FORMATTERS =
      ImmutableMap.<Pattern, DateTimeFormatter>builder()
          .put(
              Pattern.compile("[12]\\d{3}-\\d{2}-\\d{2}"),
              DateTimeFormatter.ofPattern("yyyy-MM-dd"))
          .put(
              Pattern.compile("[12]\\d{3}/\\d{2}/\\d{2}"),
              DateTimeFormatter.ofPattern("yyyy/MM/dd"))
          .build();
  private static final Function<String, Account> ACCOUNT_GENERATOR =
      (name) -> Account.newBuilder().setId(name).setName(name).build();

  private final ImmutableList<String> ledgerFileLines;

  private LedgerFileImporter(List<String> ledgerFileLines) {
    this.ledgerFileLines = ImmutableList.copyOf(ledgerFileLines);
  }

  public static LedgerFileImporter create(List<String> ledgerFileLines) {
    return new LedgerFileImporter(ledgerFileLines);
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
                "The transaction %s is not balanced!",
                spaceJoiner.join(
                    Formatter.ledgerDate(
                        Instant.ofEpochSecond(currentTransaction.getPostDateEpochSecond())),
                    currentTransaction.getDescription()));
          }
          transactionsById.put(currentTransaction.getId(), currentTransaction);
          splitsByTranscationId.putAll(currentTransaction.getId(), currentSplits);
          currentTransaction = null;
          currentSplits.clear();
        }
        continue;
      }

      List<String> tokens = spaceSplitter.splitToList(line);

      // If we've got a non-null currentTransaction it means we're in the middle of extracting it's
      // splits. Try to parse the line as one.
      if (currentTransaction != null) {
        Matcher currencyMatcher = USD_CURRENCY_PATTERN.matcher(line);
        boolean foundAmount = currencyMatcher.find();
        if (!foundAmount && currentSplits.isEmpty()) {
          LOGGER.atWarning().log("Expected to find a currency-like amount for line '%s'", line);
          continue;
        }
        BigDecimal amount =
            foundAmount
                ? new BigDecimal(line.substring(currencyMatcher.start()).trim().replace("$", ""))
                : currentSplits.stream()
                    .map(ModelTransforms::bigDecimalAmountForSplit)
                    .reduce(BigDecimal.ZERO, (first, second) -> first.add(second))
                    .negate();
        Split.Builder splitBuilder =
            ModelGenerator.splitBuilderWithAmount(amount)
                .setTransactionId(currentTransaction.getId());
        String accountName =
            foundAmount ? line.substring(0, currencyMatcher.start()).trim() : line.trim();
        if (!accountsById.containsKey(accountName)) {
          LOGGER.atInfo().log(
              "Account did not already exist by name %s. Creating it.", accountName);
          accountsById.computeIfAbsent(accountName, ACCOUNT_GENERATOR);
        }
        currentSplits.add(splitBuilder.setAccountId(accountName).build());
      } else if (tokens.get(0).equals(ACCOUNT_NAME_PREFIX)) {
        String accountName = spaceJoiner.join(FluentIterable.from(tokens).skip(1));
        if (accountsById.containsKey(accountName)) {
          LOGGER.atWarning().log(
              "Ledger file contains duplicate account declarations for: " + accountName);
          continue;
        }
        // TODO: It would be nice to fill in a more specific type for the account here.
        accountsById.computeIfAbsent(accountName, ACCOUNT_GENERATOR);
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
        "Imported %s accounts, %s transactions, and %s splits from ledger file.",
        accountsById.size(), transactionsById.size(), splitsByTranscationId.size());
    return ModelGenerator.create(
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
