package net.brentwalther.jcf.ui.swing.impl;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import java.awt.Container;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import net.brentwalther.jcf.environment.JcfEnvironment;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.string.Formatter;
import net.brentwalther.jcf.ui.swing.SwingUi;

public class SwingUiImpl implements SwingUi {

  private final JcfEnvironment jcfEnvironment;
  private final Container rootContainer;

  public SwingUiImpl(JcfEnvironment jcfEnvironment) {
    this.jcfEnvironment = jcfEnvironment;

    ImmutableMap<String, Account> includedAccountsById =
        Maps.uniqueIndex(
            FluentIterable.from(jcfEnvironment.getInitialModel().getAccountList()), Account::getId);
    ImmutableMap<String, Transaction> allTransactionsById =
        Maps.uniqueIndex(jcfEnvironment.getInitialModel().getTransactionList(), Transaction::getId);

    ImmutableMultimap<Instant, Split> splitsByDistinctInstants =
        ImmutableListMultimap.copyOf(
            FluentIterable.from(jcfEnvironment.getInitialModel().getSplitList())
                .filter(
                    s ->
                        s != null
                            && includedAccountsById.containsKey(s.getAccountId())
                            && allTransactionsById.containsKey(s.getTransactionId()))
                .transform(
                    s ->
                        s == null
                            ? Maps.immutableEntry(Instant.MIN, Split.getDefaultInstance())
                            : Maps.immutableEntry(
                                Instant.ofEpochSecond(
                                    allTransactionsById
                                        .getOrDefault(
                                            s.getTransactionId(), Transaction.getDefaultInstance())
                                        .getPostDateEpochSecond()),
                                s)));

    ImmutableList.Builder<String> lines = ImmutableList.builder();
    Map<String, BigDecimal> balances =
        Maps.newLinkedHashMapWithExpectedSize(includedAccountsById.size());
    ImmutableSortedSet<Instant> orderedDistinctTransactionInstants =
        ImmutableSortedSet.copyOf(
            Ordering.from(Instant::compareTo), splitsByDistinctInstants.keySet());
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
    while (nextCutoff.minusMonths(1).isBefore(ZonedDateTime.now())) {
      ImmutableSortedSet<Instant> distinctInstants =
          orderedDistinctTransactionInstants
              .tailSet(nextCutoff.minusMonths(1).toInstant())
              .headSet(nextCutoff.toInstant());
      if (distinctInstants.isEmpty()) {
        break;
      }
      for (Instant instant : distinctInstants) {
        for (Split split : splitsByDistinctInstants.get(instant)) {
          balances.put(
              split.getAccountId(),
              balances
                  .getOrDefault(split.getAccountId(), BigDecimal.ZERO)
                  .add(ModelTransforms.bigDecimalAmountForSplit(split)));
        }
      }
      lines.add(
          Joiner.on('\t')
              .join(
                  Iterables.concat(
                      ImmutableList.of(Formatter.date(nextCutoff.toInstant())),
                      FluentIterable.from(balances.values()).transform(Formatter::currency))));
      nextCutoff = nextCutoff.plusMonths(1);
    }
    this.rootContainer =
        new JScrollPane(
            new JTextArea(
                Joiner.on('\n')
                    .join(
                        FluentIterable.of(
                                Joiner.on('\t')
                                    .join(FluentIterable.of("Date").append(balances.keySet())))
                            .append(Lists.reverse(lines.build())))));
  }

  public static SwingUi create(JcfEnvironment jcfEnvironment) {
    return new SwingUiImpl(jcfEnvironment);
  }

  public static SwingUi dummyAppFromHelpText(String string) {
    return new ErrorUi(string);
  }

  @Override
  public Container getRootContainer() {
    return rootContainer;
  }
}
