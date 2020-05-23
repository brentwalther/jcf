package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jline.terminal.Size;

import java.util.Optional;

public class NoticePrompt implements Prompt<Object> {

  private final ImmutableList<String> messages;

  public NoticePrompt(ImmutableList<String> messages) {
    this.messages = messages;
  }

  public static NoticePrompt withMessages(ImmutableList<String> messages) {
    return new NoticePrompt(messages);
  }

  @Override
  public Optional<Object> transform(String input) {
    return Optional.of(new Object());
  }

  @Override
  public ImmutableList<String> getInstructions(Size size) {
    return messages;
  }

  @Override
  public String getPromptString() {
    return "Press any key to continue...";
  }

  @Override
  public ImmutableList<String> getStatusBars() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableSet<String> getAutoCompleteOptions() {
    return ImmutableSet.of();
  }
}
