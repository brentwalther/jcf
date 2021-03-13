package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.math.BigDecimal;

public class BigDecimalPrompt implements Prompt<BigDecimal> {

  private static final Prompt<BigDecimal> INSTANCE =
      PromptBuilder.<BigDecimal>create()
          .withPromptString("Enter the amount for the split (w/ format [+-][0-9]+[.][0-9]+):")
          .withTransformer(
              input -> {
                try {
                  return Result.bigDecimal(new BigDecimal(input));
                } catch (NumberFormatException e) {
                  return Result.empty();
                }
              })
          .build();

  public static BigDecimalPrompt create() {
    return new BigDecimalPrompt();
  }

  private BigDecimalPrompt() {
    /* Uses a shared static instance so nothing to do. */
  }

  @Override
  public Result<BigDecimal> transform(String input) {
    return INSTANCE.transform(input);
  }

  @Override
  public ImmutableList<String> getInstructions(SizeBounds size) {
    return INSTANCE.getInstructions(size);
  }

  @Override
  public String getPromptString() {
    return INSTANCE.getPromptString();
  }

  @Override
  public ImmutableList<String> getStatusBars() {
    return INSTANCE.getStatusBars();
  }

  @Override
  public ImmutableSet<String> getAutoCompleteOptions() {
    return INSTANCE.getAutoCompleteOptions();
  }

  @Override
  public boolean shouldClearScreen() {
    return INSTANCE.shouldClearScreen();
  }
}
