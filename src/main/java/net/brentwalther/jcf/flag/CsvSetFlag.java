package net.brentwalther.jcf.flag;

import com.beust.jcommander.IStringConverter;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class CsvSetFlag implements IStringConverter<Set<String>> {
  @Override
  public Set<String> convert(String s) {
    return ImmutableSet.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(s));
  }
}
