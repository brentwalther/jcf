package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jline.terminal.Size;

import java.util.List;
import java.util.Optional;

public class OptionsPrompt implements Prompt<OptionsPrompt.Choice> {

  public enum ChoiceType {
    NUMBERED_OPTION,
    AUTOCOMPLETE_OPTION,
    EMPTY
  }

  public static class Choice {
    public final ChoiceType type;
    public final Integer numberChoice;
    public final String autocompleteChoice;

    public Choice(ChoiceType type, Integer numberChoice, String autocompleteChoice) {
      this.type = type;
      this.numberChoice = numberChoice;
      this.autocompleteChoice = autocompleteChoice;
    }
  }

  private final ImmutableList<String> options;
  private final Integer defaultOption;
  private final ImmutableSet<String> autoCompleteOptions;
  private final ImmutableList<String> prefaces;

  private OptionsPrompt(
      ImmutableList<String> options,
      Integer defaultOption,
      ImmutableSet<String> autoCompleteOptions,
      ImmutableList<String> prefaces) {
    this.options = ImmutableList.copyOf(options);
    this.defaultOption = defaultOption;
    this.autoCompleteOptions = autoCompleteOptions;
    this.prefaces = prefaces;
  }

  public static OptionsPrompt create(List<String> options) {
    return builder(options).build();
  }

  public static OptionsPrompt.Builder builder(List<String> options) {
    return new Builder(options);
  }

  @Override
  public ImmutableList<String> getInstructions(Size size) {
    ImmutableList.Builder<String> instructions =
        ImmutableList.builderWithExpectedSize(prefaces.size() + options.size());
    instructions.addAll(prefaces);
    for (int i = 0; i < options.size(); i++) {
      instructions.add("(" + (i + 1) + ") " + options.get(i));
    }
    return instructions.build();
  }

  @Override
  public String getPromptString() {
    StringBuilder promptStringBuilder =
        new StringBuilder().append("Choose an option (1-").append(options.size()).append(")");
    if (defaultOption != null) {
      promptStringBuilder
          .append(" or hit enter to accept the default (")
          .append(defaultOption)
          .append(")");
    }
    return promptStringBuilder.append(": ").toString();
  }

  @Override
  public ImmutableList<String> getStatusBars() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableSet<String> getAutoCompleteOptions() {
    return autoCompleteOptions;
  }

  @Override
  public Optional<Choice> transform(String input) {
    input = input.trim();
    if (autoCompleteOptions.contains(input)) {
      return Optional.of(new Choice(ChoiceType.AUTOCOMPLETE_OPTION, null, input));
    }
    try {
      int option = -1;
      if (input.isEmpty()) {
        if (defaultOption != null) {
          option = defaultOption;
        } else {
          return Optional.of(new Choice(ChoiceType.EMPTY, null, null));
        }
      } else {
        option = Integer.parseInt(input);
      }
      if (option > 0 && option <= options.size()) {
        return Optional.of(new Choice(ChoiceType.NUMBERED_OPTION, option - 1, null));
      } else {
        return Optional.empty();
      }
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  public static class Builder {
    private final ImmutableList<String> options;
    private Integer defaultOption;
    private ImmutableList<String> prefaces;
    private ImmutableSet<String> autoCompleteOptions;

    public Builder(Iterable<String> options) {
      this.options = ImmutableList.copyOf(options);
      this.defaultOption = null;
      this.autoCompleteOptions = ImmutableSet.of();
      this.prefaces = ImmutableList.of();
    }

    public Builder withDefaultOption(int defaultOption) {
      if (defaultOption > 0 && defaultOption <= options.size()) {
        this.defaultOption = defaultOption;
      } else {
        System.err.println("Default option out of range: " + defaultOption);
      }
      return this;
    }

    public Builder withAutoCompleteOptions(Iterable<String> autoCompleteOptions) {
      this.autoCompleteOptions = ImmutableSet.copyOf(autoCompleteOptions);
      return this;
    }

    public Builder withPrefaces(Iterable<String> prefaces) {
      this.prefaces = ImmutableList.copyOf(prefaces);
      return this;
    }

    public OptionsPrompt build() {
      return new OptionsPrompt(options, defaultOption, autoCompleteOptions, prefaces);
    }
  }
}
