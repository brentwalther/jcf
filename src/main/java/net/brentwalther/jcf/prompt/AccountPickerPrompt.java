package net.brentwalther.jcf.prompt;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.math.DoubleMath;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.brentwalther.jcf.model.JcfModel.Account;

public class AccountPickerPrompt implements Prompt<Account> {

  private static final Comparator<? super Account> ACCOUNT_NAME_ORDERING =
      Ordering.natural().onResultOf(Account::getName);
  private final ImmutableMap<String, Account> accountsByName;
  private final ImmutableListMultimap<String, Account> accountsByParentId;

  private AccountPickerPrompt(ImmutableMap<String, Account> accountsByName) {
    this.accountsByName = accountsByName;
    this.accountsByParentId = Multimaps.index(accountsByName.values(), Account::getParentId);
  }

  public static AccountPickerPrompt create(Iterable<Account> accounts) {
    return new AccountPickerPrompt(Maps.uniqueIndex(accounts, Account::getName));
  }

  private static ImmutableList<String> buildSortedTree(
      ImmutableListMultimap<String, Account> accountsByParentId) {
    ImmutableMap<String, Account> accountsById =
        Maps.uniqueIndex(accountsByParentId.values(), Account::getId);
    List<Account> accountsToProcess =
        (accountsByParentId.get("").isEmpty()
            ? Lists.transform(accountsByParentId.keySet().asList(), accountsById::get)
            : accountsByParentId.get("").asList());
    return ImmutableList.copyOf(
        accountsToProcess.stream()
            .sorted(ACCOUNT_NAME_ORDERING)
            .map(account -> Node.create(account, 0))
            .flatMap(
                node ->
                    node == null
                        ? Stream.of()
                        : Stream.concat(
                            Stream.of(node),
                            accountsByParentId.get(node.account().getId()).stream()
                                .sorted(ACCOUNT_NAME_ORDERING)
                                .map(account -> Node.create(account, node.indentationLevel() + 1))))
            .map(node -> Strings.repeat("  ", node.indentationLevel()) + node.account().getName())
            .toArray(String[]::new));
  }

  @Override
  public Result<Account> transform(String input) {
    return Optional.ofNullable(accountsByName.get(input.trim()))
        .<Result<Account>>map(Result::account)
        .orElse(Result.empty());
  }

  @Override
  public ImmutableList<String> getInstructions(SizeBounds size) {
    ImmutableList<String> sortedIndentedEntries = buildSortedTree(accountsByParentId);
    if (sortedIndentedEntries.size() < size.getMaxRows()) {
      return sortedIndentedEntries;
    }
    int numColumnsNeeded =
        (sortedIndentedEntries.size() / size.getMaxRows())
            + (sortedIndentedEntries.size() % size.getMaxRows() == 0 ? 0 : 1);
    List<List<String>> columns = new ArrayList<>();
    for (int i = 0; i < numColumnsNeeded; i++) {
      int startIndex =
          DoubleMath.roundToInt(
              sortedIndentedEntries.size() * (1.0 * i / numColumnsNeeded), RoundingMode.FLOOR);
      int endIndex =
          DoubleMath.roundToInt(
              sortedIndentedEntries.size() * (1.0 * (i + 1) / numColumnsNeeded) - 1,
              RoundingMode.FLOOR);
      columns.add(sortedIndentedEntries.subList(startIndex, endIndex));
    }
    final int columnWidth = (size.getMaxCols() - 3 * (numColumnsNeeded - 1)) / numColumnsNeeded;
    ImmutableList.Builder<String> rows = ImmutableList.builder();
    for (int i = 0; i < columns.get(0).size(); i++) {
      final int index = i;
      rows.add(
          Joiner.on(" | ")
              .join(
                  FluentIterable.from(columns)
                      .transform(column -> index < column.size() ? column.get(index) : "")
                      .transform(
                          item ->
                              item.length() <= columnWidth
                                  ? Strings.padEnd(item, columnWidth, ' ')
                                  : item.substring(0, columnWidth))));
    }
    return rows.build();
  }

  @Override
  public String getPromptString() {
    return "Please choose one of the accounts. Use <TAB> to autocomplete the name:";
  }

  @Override
  public ImmutableList<String> getStatusBars() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableSet<String> getAutoCompleteOptions() {
    return accountsByName.keySet();
  }

  @Override
  public boolean shouldClearScreen() {
    return false;
  }

  @AutoValue
  abstract static class Node {
    static Node create(Account account, int indentationLevel) {
      return new AutoValue_AccountPickerPrompt_Node(indentationLevel, account);
    }

    abstract int indentationLevel();

    abstract Account account();
  }
}
