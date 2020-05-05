package net.brentwalther.jcf.prompt;

import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import net.brentwalther.jcf.model.Account;
import org.jline.terminal.Size;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;

public class AccountPickerPrompt implements Prompt<Account> {

  private static final String THIS_ACCOUNT = "X";
  private static final String GO_UP = "U";
  private final ImmutableMap<String, Account> accountsById;
  private final ImmutableMultimap<String, Account> childrenByParentId;
  private final ArrayDeque<Account> accountStack = new ArrayDeque<>();

  private AccountPickerPrompt(Map<String, Account> accountsById) {
    this.accountsById = ImmutableMap.copyOf(accountsById);
    this.childrenByParentId = Multimaps.index(accountsById.values(), (account) -> account.parentId);
    Account root = findRoot(this.accountsById);
    if (root != null) {
      accountStack.addLast(root);
    }
  }

  public static AccountPickerPrompt create(Map<String, Account> accountsById) {
    return new AccountPickerPrompt(accountsById);
  }

  private static Account findRoot(ImmutableMap<String, Account> accountsById) {
    Multiset<Account> accountParents =
        HashMultiset.create(
            FluentIterable.from(accountsById.values())
                .transform((account) -> accountsById.get(account.parentId))
                .filter(Predicates.notNull()));
    for (Account account : accountsById.values()) {
      if (!Strings.isNullOrEmpty(account.parentId)) {
        continue;
      }
      if (accountParents.count(account) > 0) {
        return account;
      }
    }
    return null;
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
    ImmutableList<Account> children = childrenByParentId.get(accountStack.getLast().id).asList();
    int numericOption = -1;
    try {
      numericOption = Integer.parseInt(input) - 1;
    } catch (NumberFormatException e) {
      // Bad input. Just return empty below.
    }
    if (numericOption >= 0 && numericOption < children.size()) {
      accountStack.addLast(children.get(numericOption));
    }
    return Optional.empty();
  }

  @Override
  public ImmutableList<String> getInstructions(Size size) {
    if (accountStack.isEmpty()) {
      return ImmutableList.of("No root account was found.");
    }
    ImmutableList.Builder<String> instructionsBuilder = ImmutableList.builder();
    Account parent = accountStack.getLast();
    ImmutableList<Account> children = childrenByParentId.get(parent.id).asList();
    instructionsBuilder.add("Current account: " + parent.name);
    instructionsBuilder.add(
        "Use option " + GO_UP + " to go up to the parent of " + parent.name + " if one exists.");
    instructionsBuilder.add("Use option " + THIS_ACCOUNT + " to choose " + parent.name);
    if (!children.isEmpty()) {
      instructionsBuilder.add("Or, choose a child:");
    }
    for (int i = 0; i < children.size(); i++) {
      instructionsBuilder.add("(" + (i + 1) + ") " + children.get(i).name);
    }
    return instructionsBuilder.build();
  }

  @Override
  public String getPromptString() {
    if (accountStack.isEmpty()) {
      return "Press Ctrl+C to escape...";
    }
    int numChildren = childrenByParentId.get(accountStack.getLast().id).size();
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
    return ImmutableList.of();
  }
}
