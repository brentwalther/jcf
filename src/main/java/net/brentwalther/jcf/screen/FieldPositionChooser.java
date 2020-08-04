package net.brentwalther.jcf.screen;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.brentwalther.jcf.CsvMatcher;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FieldPositionChooser {
  public static ImmutableMap<CsvMatcher.CsvField, Integer> getPositionsFor(String line) {

    BiMap<CsvMatcher.CsvField, Integer> mappings = EnumHashBiMap.create(CsvMatcher.CsvField.class);
    List<String> fields = Splitter.on(',').trimResults().splitToList(line);

    Set<CsvMatcher.CsvField> unsetFields = Sets.newHashSet(CsvMatcher.CsvField.values());
    while (!hasNecessaryFieldsSet(mappings)) {

      List<String> fieldOptionStrings = new ArrayList<>();
      for (int i = 0; i < fields.size(); i++) {
        String field = fields.get(i);
        fieldOptionStrings.add(
            field
                + " -> "
                + (mappings.inverse().containsKey(i)
                    ? Objects.toString(mappings.inverse().get(i))
                    : "(unassigned)"));
      }
      OptionsPrompt.Choice fieldChoice =
          PromptEvaluator.showAndGetResult(
              TerminalProvider.get(),
              OptionsPrompt.builder(fieldOptionStrings)
                  .withPrefaces(
                      ImmutableList.of(
                          "You have not assigned a column for the following fields: "
                              + Joiner.on(", ").join(unsetFields),
                          "",
                          "Please choose a column to assign to a field:"))
                  .withAutoCompleteOptions(fields)
                  .build());

      if (fieldChoice == null) {
        // The user is probably trying to abort. Go ahead and quit the screen.
        break;
      }

      Integer fieldIndex = -1;
      switch (fieldChoice.type) {
        case NUMBERED_OPTION:
          fieldIndex = fieldChoice.numberChoice;
          break;
        case AUTOCOMPLETE_OPTION:
          fieldIndex = fields.indexOf(fieldChoice.autocompleteChoice);
          break;
      }

      if (fieldIndex == -1) {
        continue;
      }

      List<CsvMatcher.CsvField> assignmentOptions = ImmutableList.copyOf(unsetFields);
      OptionsPrompt.Choice assignment =
          PromptEvaluator.showAndGetResult(
              TerminalProvider.get(),
              OptionsPrompt.builder(Lists.transform(assignmentOptions, Objects::toString))
                  .withPrefaces(
                      ImmutableList.of(
                          "Please choose the column to assign to field \""
                              + fields.get(fieldIndex)
                              + "\":"))
                  .withAutoCompleteOptions(Lists.transform(assignmentOptions, Objects::toString))
                  .build());

      CsvMatcher.CsvField assignedField = null;
      switch (assignment.type) {
        case NUMBERED_OPTION:
          assignedField = assignmentOptions.get(assignment.numberChoice);
          break;
        case AUTOCOMPLETE_OPTION:
          assignedField =
              assignmentOptions.get(
                  Lists.transform(assignmentOptions, Objects::toString)
                      .indexOf(assignment.autocompleteChoice));
      }

      if (assignedField != null) {
        mappings.put(assignedField, fieldIndex);
        unsetFields.remove(assignedField);
      }
    }
    return ImmutableMap.<CsvMatcher.CsvField, Integer>builder()
        .putAll(mappings)
        .putAll(Iterables.transform(unsetFields, field -> Maps.immutableEntry(field, -1)))
        .build();
  }

  private static boolean hasNecessaryFieldsSet(Map<CsvMatcher.CsvField, Integer> positions) {
    return positions.containsKey(CsvMatcher.CsvField.DESCRIPTION)
        && positions.containsKey(CsvMatcher.CsvField.DATE)
        && (positions.containsKey(CsvMatcher.CsvField.AMOUNT)
            || (positions.containsKey(CsvMatcher.CsvField.CREDIT)
                && positions.containsKey(CsvMatcher.CsvField.DEBIT)));
  }
}
