package net.brentwalther.jcf.screen;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.Prompt;
import net.brentwalther.jcf.prompt.PromptBuilder;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.util.Formatter;

import javax.annotation.Nullable;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class DateTimeFormatChooser {

  @Nullable
  public static DateTimeFormatter obtainFormatForExamples(Iterable<String> examples) {
    ImmutableList<String> exampleData = ImmutableList.copyOf(examples);

    Prompt<String> optionsPrompt =
        PromptBuilder.<String>create()
            .withInstructions(
                ImmutableList.of(
                    "You need to specify a format for a date that looks like this:",
                    "---",
                    Joiner.on(", ").join(exampleData),
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
                    "or, enter Q to (Q)uit"))
            .withTransformer((input) -> Optional.of(input))
            .build();

    while (true) {
      String input = PromptEvaluator.showAndGetResult(TerminalProvider.get(), optionsPrompt);
      if (input == null || input.equalsIgnoreCase("q")) {
        // The user wants to exit so exit. The caller upstream will need to decide whether it
        // can continue with a null result.
        break;
      }

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(input);
      boolean anyExampleUnparseable = false;
      for (String example : exampleData) {
        try {
          Formatter.parseDateFrom(example, formatter);
        } catch (DateTimeException e) {
          anyExampleUnparseable = true;
          PromptEvaluator.showAndGetResult(
              TerminalProvider.get(), NoticePrompt.withMessages(ImmutableList.of(e.toString())));
          break;
        }
      }
      if (anyExampleUnparseable) {
        continue;
      }

      return formatter;
    }

    return null;
  }
}
