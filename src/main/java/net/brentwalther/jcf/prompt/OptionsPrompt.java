package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import org.jline.terminal.Size;

import java.util.List;
import java.util.Optional;

public class OptionsPrompt implements Prompt<Integer> {

  private final ImmutableList<String> options;
  private final Integer defaultOption;
  private final ImmutableList<String> prefaces;

  private OptionsPrompt(
      List<String> options, Integer defaultOption, ImmutableList<String> prefaces) {
    this.options = ImmutableList.copyOf(options);
    this.defaultOption = defaultOption;
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
  public Optional<Integer> transform(String input) {
    try {
      int option = -1;
      if (input.isEmpty() && defaultOption != null) {
        option = defaultOption;
      } else {
        option = Integer.parseInt(input);
      }
      if (option > 0 && option <= options.size()) {
        return Optional.of(option - 1);
      } else {
        return Optional.empty();
      }
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  public static class Builder {
    private final List<String> options;
    private Integer defaultOption;
    private ImmutableList<String> prefaces;

    public Builder(List<String> options) {
      this.options = options;
      this.defaultOption = null;
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

    public Builder withPrefaces(ImmutableList<String> prefaces) {
      this.prefaces = prefaces;
      return this;
    }

    public OptionsPrompt build() {
      return new OptionsPrompt(options, defaultOption, prefaces);
    }
  }
}
