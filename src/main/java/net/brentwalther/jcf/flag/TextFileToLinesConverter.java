package net.brentwalther.jcf.flag;

import com.beust.jcommander.IStringConverter;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.brentwalther.jcf.flag.TextFileToLinesConverter.EagerlyLoadedTextFile;

public class TextFileToLinesConverter implements IStringConverter<EagerlyLoadedTextFile> {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  @Override
  public EagerlyLoadedTextFile convert(String fileName) {
    File file = new File(fileName);
    if (!file.isFile()) {
      return EagerlyLoadedTextFile.EMPTY;
    }
    try {
      ImmutableList<String> lines = ImmutableList.copyOf(Files.readAllLines(file.toPath()));
      return () -> lines;
    } catch (IOException e) {
      LOGGER.atWarning().withCause(e).log("Could not eagerly load text file: %s", file);
      return EagerlyLoadedTextFile.EMPTY;
    }
  }

  public interface EagerlyLoadedTextFile {
    EagerlyLoadedTextFile EMPTY =
        new EagerlyLoadedTextFile() {
          @Override
          public ImmutableList<String> lines() {
            return ImmutableList.of();
          }

          @Override
          public String toString() {
            return "Empty file.";
          }
        };

    ImmutableList<String> lines();
  }
}
