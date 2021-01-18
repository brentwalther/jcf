package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;

public class FilePrompt implements Prompt<File> {

  private final boolean mustExist;

  private FilePrompt(boolean mustExist) {
    this.mustExist = mustExist;
  }

  public static FilePrompt anyFile() {
    return new FilePrompt(/* mustExist= */ false);
  }

  public static FilePrompt existingFile() {
    return new FilePrompt(/* mustExist= */ true);
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

  @Override
  public String getPromptString() {
    return "Enter the file name:";
  }

  @Override
  public Result<File> transform(String input) {
    File file = new File(input);
    if ((!file.exists() && mustExist)) {
      return Result.empty();
    }
    if (!file.exists()) {
      try {
        boolean success = file.createNewFile();
        if (!success) {
          return Result.empty();
        }
      } catch (IOException e) {
        return Result.empty();
      }
    }
    if (file.exists() && !file.isFile()) {
      return Result.empty();
    }
    return Result.file(file);
  }

  @Override
  public ImmutableList<String> getInstructions(SizeBounds size) {
    return ImmutableList.of();
  }
}
