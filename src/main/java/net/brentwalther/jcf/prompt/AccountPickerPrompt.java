package net.brentwalther.jcf.prompt;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import net.brentwalther.jcf.model.JcfModel.Account;
import org.jline.terminal.Size;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

public class AccountPickerPrompt implements Prompt<Account> {

  /**
   * If we find numerous roots (accounts with no parent), there is probably no official "root"
   * account. Instead, we can just insert this fake one using the empty string as it's ID and start
   * the search here.
   */
  private static final Account FAKE_ROOT_ACCOUNT =
      Account.newBuilder().setId("").setName("Fake Root Account").build();

  private static final String THIS_ACCOUNT = "X";
  private static final String GO_UP = "U";
  private final ImmutableSortedMap<String, Account> accountsByName;
  private final ImmutableListMultimap<String, Account> childrenByParentId;
  private final ArrayDeque<Account> accountStack = new ArrayDeque<>();

  private AccountPickerPrompt(Map<String, Account> accountsById) {
    this.accountsByName =
        ImmutableSortedMap.copyOf(
            FluentIterable.from(accountsById.values())
                .transform(account -> Maps.immutableEntry(account.getName(), account)));
    this.childrenByParentId = Multimaps.index(accountsById.values(), Account::getParentId);
    ImmutableList<Account> sortedOrphans =
        FluentIterable.from(accountsById.values())
            .filter(account -> account != null && account.getParentId().isEmpty())
            .toSortedList(Comparator.comparing(Account::getName));
    if (sortedOrphans.size() == 1) {
      // We only found one account without a parent. This must be the root.
      accountStack.addLast(sortedOrphans.get(0));
    } else {
      // More than one account had no parent. So, insert the fake root which has an empty
      // ID, allowing it to match these orphans (with empty parent ID).
      accountStack.addLast(FAKE_ROOT_ACCOUNT);
    }
  }

  public static AccountPickerPrompt create(Map<String, Account> accountsById) {
    return new AccountPickerPrompt(accountsById);
  }

  @Override
  public Optional<Account> transform(String input) {
    if (GO_UP.equals(input)) {
      if (accountStack.size() > 1) {
        accountStack.pollLast();
      }
      return Optional.empty();
    }
    if (THIS_ACCOUNT.equals(input)) {
      return Optional.of(accountStack.getLast());
    }
    ImmutableList<Account> children =
        childrenByParentId.get(accountStack.getLast().getId()).asList();
    int numericOption = -1;
    try {
      numericOption = Integer.parseInt(input) - 1;
    } catch (NumberFormatException e) {
      // Bad input. Just return empty below.
    }
    if (numericOption >= 0 && numericOption < children.size()) {
      accountStack.addLast(children.get(numericOption));
    } else if (accountsByName.containsKey(input)) {
      return Optional.of(accountsByName.get(input));
    }
    return Optional.empty();
  }

  @Override
  public ImmutableList<String> getInstructions(Size size) {
    ImmutableList.Builder<String> instructionsBuilder = ImmutableList.builder();
    Account currentAccount = currentAccount();
    ImmutableList<Account> children = childrenByParentId.get(currentAccount.getId()).asList();
    if (accountStack.size() > 1) {
      instructionsBuilder.add(
          "Use option " + GO_UP + " to go up to the parent of " + currentAccount.getName());
    }
    instructionsBuilder.add(
        "Use option " + THIS_ACCOUNT + " to choose " + currentAccount.getName());
    if (!children.isEmpty()) {
      instructionsBuilder.add("Or, choose a child:");
      // Show up to the first 10 children.
      for (int i = 0; i < Math.min(children.size(), 10); i++) {
        instructionsBuilder.add("(" + (i + 1) + ") " + children.get(i).getName());
      }
    }
    instructionsBuilder.add(
        "Or, you can use <TAB> to auto-complete your input to a known account name.");
    return instructionsBuilder.build();
  }

  @Override
  public String getPromptString() {
    int numChildren = childrenByParentId.get(accountStack.getLast().getId()).size();
    StringBuilder promptStringBuilder =
        new StringBuilder()
            .append("Choose an option (")
            .append(THIS_ACCOUNT)
            .append(", ")
            .append(GO_UP);
    if (numChildren > 0) {
      promptStringBuilder.append(", or 1-" + numChildren);
    }
    return promptStringBuilder.append("): ").toString();
  }

  @Override
  public ImmutableList<String> getStatusBars() {
    return ImmutableList.of("Current account: " + currentAccount().getName());
  }

  @Override
  public ImmutableSet<String> getAutoCompleteOptions() {
    return accountsByName.keySet();
  }

  private Account currentAccount() {
    return accountStack.getLast();
  }
}
