package net.brentwalther.jcf.prompt;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import net.brentwalther.jcf.string.Formatter;

public class DateTimeFormatPrompt implements Prompt<DateTimeFormatter> {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private final ImmutableList<String> examples;

  public DateTimeFormatPrompt(Iterable<String> examples) {
    this.examples = ImmutableList.copyOf(examples);
  }

  public static DateTimeFormatPrompt usingExamples(Iterable<String> examples) {
    return new DateTimeFormatPrompt(examples);
  }

  @Override
  public Result<DateTimeFormatter> transform(String input) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(input);
    boolean anyExampleUnparseable = false;
    for (String example : examples) {
      try {
        Formatter.parseDateFrom(example, formatter);
      } catch (DateTimeException e) {
        LOGGER.atWarning().withCause(e).log(
            "Could not use pattern %s to parse the example %s", input, example);
        anyExampleUnparseable = true;
        break;
      }
    }

    return anyExampleUnparseable ? Result.empty() : Result.dateTimeFormatter(formatter);
  }

  @Override
  public ImmutableList<String> getInstructions(SizeBounds size) {
    return ImmutableList.of(
        "You need to specify a format for a date that looks like this:",
        "---",
        Joiner.on(", ").join(examples),
        "",
        "Please use the following formats specifiers:",
        "---",
        "y       year-of-era                 year              2004; 04",
        "M/MM/L  month-of-year               number/text       7 / 07 / Jul; July; J",
        "d       day-of-month                number            10",
        "a       am-pm-of-day                text              PM",
        "h       clock-hour-of-am-pm (1-12)  number            12",
        "k       clock-hour-of-am-pm (1-24)  number            0",
        "K       hour-of-am-pm (0-11)        number            0",
        "H       hour-of-day (0-23)          number            0",
        "m       minute-of-hour              number            30",
        "s       second-of-minute            number            55",
        "S       fraction-of-second          fraction          978",
        "V       time-zone ID                zone-id           America/Los_Angeles; Z; -08:30",
        "z       time-zone name              zone-name         Pacific Standard Time; PST",
        "O       localized zone-offset       offset-O          GMT+8; GMT+08:00; UTC-08:00;",
        "",
        "Examples: y/M/d MM-d-y",
        "",
        "or, enter Q to (Q)uit");
  }

  @Override
  public String getPromptString() {
    return "Enter something:";
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
