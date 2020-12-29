package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PromptEvaluator<T> {

  public static <T> T showAndGetResult(Terminal terminal, Prompt<T> prompt) {
    Optional<T> result = Optional.empty();
    do {
      if (prompt.shouldClearScreen()) {
        terminal.puts(InfoCmp.Capability.clear_screen);
      }
      Size size = terminal.getSize();
      printInstructions(prompt, terminal.writer(), size);
      terminal.flush();
      String input;
      try {
        input =
            LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new StringsCompleter(prompt.getAutoCompleteOptions()))
                .build()
                .readLine(prompt.getPromptString().trim() + " ");
      } catch (UserInterruptException e) {
        return null;
      }
      result = prompt.transform(input);
    } while (!result.isPresent());

    return result.get();
  }

  private static <T> void printInstructions(Prompt<T> prompt, PrintWriter writer, Size size) {
    ImmutableList<String> statusBars = prompt.getStatusBars();
    // The amount of room you have for instructions is reduced by the status bars and the
    // prompt line itself.
    List<String> instructions = new ArrayList<>();
    instructions.add(panelTopWithWidth(size.getColumns()));
    if (!statusBars.isEmpty()) {
      instructions.addAll(
          Lists.transform(
              statusBars,
              barText -> PromptEvaluator.wrapInVerticalLines(size.getColumns(), barText)));
      instructions.add(panelDividerWithWidth(size.getColumns()));
    }
    instructions.addAll(
        Lists.transform(
            prompt.getInstructions(
                new Size(size.getColumns() - 2, size.getRows() - instructions.size() - 1)),
            instruction -> PromptEvaluator.wrapInVerticalLines(size.getColumns(), instruction)));
    instructions.add(panelBottomWithWidth(size.getColumns()));

    // Flush them all to the writer.
    instructions.forEach(writer::println);
  }

  private static String wrapInVerticalLines(int width, String base) {
    return SpecialCharacters.VERTICAL_LINE
        + rightPadOrTruncate(base, ' ', width - 2)
        + SpecialCharacters.VERTICAL_LINE;
  }

  private static String rightPadOrTruncate(String base, char padChar, int width) {
    String truncated = base.substring(0, Math.min(base.length(), width));
    return truncated + duplicate(Character.toString(padChar), width - truncated.length());
  }

  private static String panelTopWithWidth(int width) {
    return SpecialCharacters.TOP_LEFT_CORNER
        + duplicate(SpecialCharacters.HORIZONTAL_LINE, width - 2)
        + SpecialCharacters.TOP_RIGHT_CORNER;
  }

  private static String panelDividerWithWidth(int width) {
    return SpecialCharacters.LEFT_DIVIDER
        + duplicate(SpecialCharacters.HORIZONTAL_LINE, width - 2)
        + SpecialCharacters.RIGHT_DIVIDER;
  }

  private static String panelBottomWithWidth(int width) {
    return SpecialCharacters.BOTTOM_LEFT_CORNER
        + duplicate(SpecialCharacters.HORIZONTAL_LINE, width - 2)
        + SpecialCharacters.BOTTOM_RIGHT_CORNER;
  }

  private static String duplicate(String s, int times) {
    if (times == 0) {
      return "";
    }
    if (times == 1) {
      return s;
    }
    StringBuilder builder = new StringBuilder(s.length() * times);
    while (times-- > 0) {
      builder.append(s);
    }
    return builder.toString();
  }
}
