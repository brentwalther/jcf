package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import org.jline.terminal.Size;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class FilePrompt implements Prompt<File> {

  private final boolean mustExist;

  public FilePrompt(boolean mustExist) {
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
  public String getPromptString() {
    return "Enter the file name:";
  }

  @Override
  public Optional<File> transform(String input) {
    File file = new File(input);
    if ((!file.exists() && mustExist)) {
      return Optional.empty();
    }
    if (!file.exists()) {
      try {
        boolean success = file.createNewFile();
        if (!success) {
          return Optional.empty();
        }
      } catch (IOException e) {
        return Optional.empty();
      }
    }
    if (file.exists() && !file.isFile()) {
      return Optional.empty();
    }
    return Optional.of(file);
  }

  @Override
  public ImmutableList<String> getInstructions(Size size) {
    return ImmutableList.of();
  }
}
