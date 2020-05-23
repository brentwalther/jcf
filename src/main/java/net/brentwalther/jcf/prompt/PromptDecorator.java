package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jline.terminal.Size;

import java.util.Optional;

public class PromptDecorator<T> implements Prompt<T> {

  private final Prompt<T> delegate;

  private PromptDecorator(Prompt<T> delegate) {
    this.delegate = delegate;
  }

  public static <T> Prompt<T> decorateWithStatusBars(
      Prompt<T> prompt, ImmutableList<String> statusBars) {
    return new PromptDecorator<T>(prompt) {
      @Override
      public ImmutableList<String> getStatusBars() {
        return statusBars;
      }
    };
  }

  @Override
  public Optional<T> transform(String input) {
    return delegate.transform(input);
  }

  @Override
  public ImmutableList<String> getInstructions(Size size) {
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
}
