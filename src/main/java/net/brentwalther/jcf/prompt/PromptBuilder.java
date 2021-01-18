package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.function.Function;
import net.brentwalther.jcf.prompt.Prompt.Result;

public class PromptBuilder<T> {

  private static final String DEFAULT_PROMPT_STRING = "Enter something or press Ctrl+C to escape:";

  private ImmutableList<String> instructions = ImmutableList.of();
  private Function<String, Result<T>> transformer = (input) -> Result.empty();
  private String promptString = DEFAULT_PROMPT_STRING;

  public static <TT> PromptBuilder<TT> create() {
    return new PromptBuilder<>();
  }

  public PromptBuilder<T> withInstructions(List<String> instructions) {
    this.instructions = ImmutableList.copyOf(instructions);
    return this;
  }

  public PromptBuilder<T> withTransformer(Function<String, Result<T>> transformer) {
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
      public Result<T> transform(String input) {
        return transformer.apply(input);
      }

      @Override
      public ImmutableList<String> getInstructions(SizeBounds size) {
        return instructions.size() > size.getMaxRows()
            ? instructions.subList(instructions.size() - size.getMaxRows(), instructions.size())
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
