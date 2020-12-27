package net.brentwalther.jcf.flag;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.IStringConverterFactory;
import com.google.common.collect.ImmutableMap;

public class JcfEnvironmentFlagFactory implements IStringConverterFactory {
  public static final IStringConverterFactory INSTANCE = new JcfEnvironmentFlagFactory();
  private static final ImmutableMap<Class, Class<? extends IStringConverter<?>>> CONVERTERS =
      ImmutableMap.of(
          TextFileToLinesConverter.EagerlyLoadedTextFile.class, TextFileToLinesConverter.class);

  @Override
  public Class<? extends IStringConverter<?>> getConverter(Class<?> aClass) {
    return CONVERTERS.get(aClass);
  }

  /** Do not instantiate. Use {@link instance}. */
  private JcfEnvironmentFlagFactory() {}
}
