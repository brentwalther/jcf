package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class PromptDecorator<T> implements Prompt<T> {

  private final Prompt<T> delegate;

  private PromptDecorator(Prompt<T> delegate) {
    this.delegate = delegate;
  }

  public static <TT> Prompt<TT> topStatusBars(Prompt<TT> prompt, ImmutableList<String> statusBars) {
    return new PromptDecorator<TT>(prompt) {
      @Override
      public ImmutableList<String> getStatusBars() {
        return ImmutableList.<String>builder()
            .addAll(statusBars)
            .addAll(prompt.getStatusBars())
            .build();
      }
    };
  }

  @Override
  public Result<T> transform(String input) {
    return delegate.transform(input);
  }

  @Override
  public ImmutableList<String> getInstructions(SizeBounds size) {
    return delegate.getInstructions(size);
  }

  @Override
  public String getPromptString() {
    return delegate.getPromptString();
  }

  @Override
  public ImmutableList<String> getStatusBars() {
    return delegate.getStatusBars();
  }

  @Override
  public ImmutableSet<String> getAutoCompleteOptions() {
    return delegate.getAutoCompleteOptions();
  }

  @Override
  public boolean shouldClearScreen() {
    return delegate.shouldClearScreen();
  }
}
