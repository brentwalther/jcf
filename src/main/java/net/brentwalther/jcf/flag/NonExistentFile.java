package net.brentwalther.jcf.flag;

import com.beust.jcommander.IStringConverter;

import java.io.File;
import java.util.Optional;

public class NonExistentFile implements IStringConverter<Optional<File>> {
  @Override
  public Optional<File> convert(String fileName) {
    File file = new File(fileName);
    if (file.isFile()) {
      return Optional.empty();
    }
    return Optional.of(file);
  }
}
