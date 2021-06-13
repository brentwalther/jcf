package net.brentwalther.jcf.report;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import net.brentwalther.jcf.model.CompleteSplit;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.string.Formatter;

public class TsvExpensesByMonthReport {
  public static String generateFrom(Model initialModel) {
    return generateFrom(IndexedModel.create(initialModel));
  }

  public static String generateFrom(IndexedModel model) {
    ImmutableMultimap<Instant, CompleteSplit> splitsByDistinctInstants =
        ImmutableListMultimap.copyOf(
            FluentIterable.from(model.getAllSplits())
                .filter(
                    s ->
                        s != null
                            && model.getAccountById(s.getAccountId()).isPresent()
                            && model.getTransactionById(s.getTransactionId()).isPresent())
                .transform(
                    split ->
                        Maps.immutableEntry(
                            Instant.ofEpochSecond(
                                model
                                    .getTransactionById(split.getTransactionId())
                                    .get()
                                    .getPostDateEpochSecond()),
                            CompleteSplit.create(
                                split,
                                model.getAccountById(split.getAccountId()).get(),
                                model.getTransactionById(split.getTransactionId()).get()))));

    ImmutableSortedSet<Instant> orderedDistinctTransactionInstants =
        ImmutableSortedSet.copyOf(Ordering.natural(), splitsByDistinctInstants.keySet());
    ZonedDateTime firstDate =
        orderedDistinctTransactionInstants.first().atZone(ZoneId.systemDefault());
    ZonedDateTime nextCutoff =
        LocalDateTime.of(
                firstDate.getYear(),
                firstDate.getMonth(),
                /* dayOfMonth= */ 1,
                /* hour= */ 0,
                /* minute= */ 0,
                /* second= */ 0)
            .atZone(ZoneId.systemDefault())
            .plusMonths(1);
    Map<Account, BigDecimal> balances =
        Maps.newLinkedHashMapWithExpectedSize(model.getAllAccounts().size());
    ImmutableList.Builder<String> lines = ImmutableList.builder();
    while (nextCutoff.minusMonths(1).isBefore(ZonedDateTime.now())) {
      // Zero out all known balances before proceeding to the next month.
      for (Account account : balances.keySet()) {
        balances.put(account, BigDecimal.ZERO);
      }
      ImmutableSortedSet<Instant> distinctInstants =
          orderedDistinctTransactionInstants
              .tailSet(nextCutoff.minusMonths(1).toInstant())
              .headSet(nextCutoff.toInstant());
      if (distinctInstants.isEmpty()) {
        break;
      }
      for (Instant instant : distinctInstants) {
        for (CompleteSplit split : splitsByDistinctInstants.get(instant)) {
          balances.put(
              split.account(),
              balances
                  .getOrDefault(split.account(), BigDecimal.ZERO)
                  .add(ModelTransforms.bigDecimalAmountForSplit(split.split())));
        }
      }
      lines.add(
          FluentIterable.of(Formatter.date(nextCutoff.toInstant()))
              .append(FluentIterable.from(balances.values()).transform(Formatter::currency))
              .join(Joiner.on('\t')));
      nextCutoff = nextCutoff.plusMonths(1);
    }
    return FluentIterable.of(
            FluentIterable.of("Date")
                .append(FluentIterable.from(balances.keySet()).transform(Account::getName))
                .join(Joiner.on('\t')))
        .append(Lists.reverse(lines.build()))
        .join(Joiner.on('\n'));
  }
}
