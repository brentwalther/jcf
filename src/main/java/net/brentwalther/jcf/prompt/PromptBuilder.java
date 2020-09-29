package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jline.terminal.Size;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class PromptBuilder<T> {

  private static final String DEFAULT_PROMPT_STRING = "Enter something or press Ctrl+C to escape:";

  private ImmutableList<String> instructions = ImmutableList.of();
  private Function<String, Optional<T>> transformer = (input) -> Optional.empty();
  private String promptString = DEFAULT_PROMPT_STRING;

  public static <T> PromptBuilder<T> create() {
    return new PromptBuilder<T>();
  }

  public PromptBuilder<T> withInstructions(List<String> instructions) {
    this.instructions = ImmutableList.copyOf(instructions);
    return this;
  }

  public PromptBuilder<T> withTransformer(Function<String, Optional<T>> transformer) {
    this.transformer = transformer;
    return this;
  }

  public PromptBuilder<T> withPromptString(String promptString) {
    this.promptString = promptString;
    return this;
  }

  public Prompt<T> build() {
    return new Prompt<T>() {
      @Override
      public Optional<T> transform(String input) {
        return transformer.apply(input);
      }

      @Override
      public ImmutableList<String> getInstructions(Size size) {
        return instructions.size() > size.getRows()
            ? instructions.subList(instructions.size() - size.getRows(), instructions.size())
            : instructions;
      }

      @Override
      public String getPromptString() {
        return promptString;
      }

      @Override
      public ImmutableList<String> getStatusBars() {
        return ImmutableList.of();
      }

      @Override
      public ImmutableSet<String> getAutoCompleteOptions() {
        return ImmutableSet.of();
      }

      @Override
      public boolean shouldClearScreen() {
        return false;
      }
    };
  }
}
