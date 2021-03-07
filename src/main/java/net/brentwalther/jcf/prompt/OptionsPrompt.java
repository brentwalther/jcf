package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;

public class OptionsPrompt implements Prompt<String> {

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
  public ImmutableList<String> getInstructions(SizeBounds size) {
    ImmutableList.Builder<String> instructions =
        ImmutableList.builderWithExpectedSize(prefaces.size() + options.size() + 1);
    instructions.addAll(prefaces);
    instructions.add("");
    for (int i = 0; i < options.size(); i++) {
      instructions.add("(" + (i + 1) + (i < 9 ? ")  " : ") ") + options.get(i));
    }
    return instructions.build();
  }

  @Override
  public String getPromptString() {
    StringBuilder promptStringBuilder =
        new StringBuilder()
            .append("Enter an option number (1 of ")
            .append(options.size())
            .append(")");
    if (!autoCompleteOptions.isEmpty()) {
      promptStringBuilder
          .append(", type a character and hit tab to autocomplete from, ")
          .append(autoCompleteOptions.size())
          .append(" options,");
    }
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
  public boolean shouldClearScreen() {
    return false;
  }

  @Override
  public Result<String> transform(String input) {
    input = input.trim();
    if (autoCompleteOptions.contains(input) || options.contains(input)) {
      return Result.string(input);
    }
    int option = -1;
    if (input.isEmpty()) {
      if (defaultOption != null) {
        option = defaultOption;
      }
    } else {
      try {
        option = Integer.parseInt(input);
      } catch (NumberFormatException e) {
        // Nothing to do as option is already set to -1
      }
    }
    return option > 0 && option <= options.size()
        ? Result.string(options.get(option - 1))
        : Result.empty();
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
