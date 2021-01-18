package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class NoticePrompt implements Prompt<Void> {

  private final ImmutableList<String> messages;

  private NoticePrompt(ImmutableList<String> messages) {
    this.messages = messages;
  }

  public static NoticePrompt withMessages(ImmutableList<String> messages) {
    return new NoticePrompt(messages);
  }

  @Override
  public Result<Void> transform(String input) {
    return Result.empty();
  }

  @Override
  public ImmutableList<String> getInstructions(SizeBounds size) {
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

  @Override
  public boolean shouldClearScreen() {
    return false;
  }
}
