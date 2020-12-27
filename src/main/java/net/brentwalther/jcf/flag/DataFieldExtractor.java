package net.brentwalther.jcf.flag;

import com.beust.jcommander.IStringConverter;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import net.brentwalther.jcf.SettingsProto.SettingsProfile.DataField;

import java.util.List;

public class DataFieldExtractor implements IStringConverter<ImmutableMap<DataField, Integer>> {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  private static final ImmutableList<DataField> DATA_FIELDS =
      ImmutableList.copyOf(DataField.values());
  public static final ImmutableList<String> DATA_FIELD_NAMES =
      ImmutableList.copyOf(Lists.transform(DATA_FIELDS, (field) -> field.toString()));
  private static final Splitter CSV_SPLITTER = Splitter.on(',');

  @Override
  public ImmutableMap<DataField, Integer> convert(String flagValue) {
    List<String> inputFieldNames = CSV_SPLITTER.splitToList(flagValue);
    // Ensure all fields are properly defined.
    if (inputFieldNames.stream()
        .anyMatch(
            field -> field != null && !field.isEmpty() && !DATA_FIELD_NAMES.contains(field))) {
      LOGGER.atSevere().log(
          "The CSV column (field) ordering is not properly defined: %s\nIt should be a csv string only containing fields: [%s]\nExiting.",
          flagValue, Joiner.on(',').join(DATA_FIELD_NAMES));
      System.exit(1);
    }
    return ImmutableMap.copyOf(
        FluentIterable.from(DATA_FIELDS)
            .transform(
                field -> Maps.immutableEntry(field, inputFieldNames.indexOf(field.toString())))
            .filter(entry -> entry.getValue() > -1));
  }
}
