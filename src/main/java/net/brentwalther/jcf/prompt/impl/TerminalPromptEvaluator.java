package net.brentwalther.jcf.prompt.impl;

import static com.google.common.base.Strings.repeat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import net.brentwalther.jcf.prompt.Prompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.prompt.SizeBounds;
import net.brentwalther.jcf.prompt.SpecialCharacters;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

public class TerminalPromptEvaluator implements PromptEvaluator {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private static final int MAX_NUMBER_OF_TRANSFORMATION_ATTEMPTS = 3;
  private final Terminal terminal;

  private TerminalPromptEvaluator(Terminal terminal) {
    this.terminal = terminal;
  }

  public static TerminalPromptEvaluator createOrDie() {

    try {
      return new TerminalPromptEvaluator(TerminalBuilder.terminal());
    } catch (IOException e) {
      LOGGER.atSevere().withCause(e).log("Could not initialize terminal API. Exiting.");
      System.exit(1);
    }
    return null;
  }

  private static void printInstructions(Prompt<?> prompt, PrintWriter writer, Size size) {
    if (prompt.getStatusBars().isEmpty()
        && prompt.getInstructions(SizeBounds.create(size.getRows(), size.getColumns())).isEmpty()) {
      return;
    }
    List<String> instructions = new ArrayList<>();
    instructions.add(panelTopWithWidth(size.getColumns()));
    ImmutableList<String> statusBars = prompt.getStatusBars();
    if (!statusBars.isEmpty()) {
      instructions.addAll(
          Lists.transform(
              statusBars,
              barText -> TerminalPromptEvaluator.wrapInVerticalLines(size.getColumns(), barText)));
      instructions.add(panelDividerWithWidth(size.getColumns()));
    }
    // The amount of room you have for instructions is reduced by the status bars and the
    // prompt line itself.
    SizeBounds bounds =
        SizeBounds.create(size.getRows() - instructions.size() - 1, size.getColumns() - 2);
    instructions.addAll(
        Lists.transform(
            prompt.getInstructions(bounds),
            instruction ->
                TerminalPromptEvaluator.wrapInVerticalLines(size.getColumns(), instruction)));
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
    return truncated + repeat(Character.toString(padChar), width - truncated.length());
  }

  private static String panelTopWithWidth(int width) {
    return SpecialCharacters.TOP_LEFT_CORNER
        + repeat(SpecialCharacters.HORIZONTAL_LINE, width - 2)
        + SpecialCharacters.TOP_RIGHT_CORNER;
  }

  private static String panelDividerWithWidth(int width) {
    return SpecialCharacters.LEFT_DIVIDER
        + repeat(SpecialCharacters.HORIZONTAL_LINE, width - 2)
        + SpecialCharacters.RIGHT_DIVIDER;
  }

  private static String panelBottomWithWidth(int width) {
    return SpecialCharacters.BOTTOM_LEFT_CORNER
        + repeat(SpecialCharacters.HORIZONTAL_LINE, width - 2)
        + SpecialCharacters.BOTTOM_RIGHT_CORNER;
  }

  @Override
  public <T> Result<T> blockingGetResult(Prompt<T> prompt) {
    if (prompt.shouldClearScreen()) {
      terminal.puts(InfoCmp.Capability.clear_screen);
    }
    Size size = terminal.getSize();
    printInstructions(prompt, terminal.writer(), size);
    terminal.flush();
    try {
      for (int i = 0; i < MAX_NUMBER_OF_TRANSFORMATION_ATTEMPTS; i++) {
        String input =
            LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new StringsCompleter(prompt.getAutoCompleteOptions()))
                .build()
                .readLine(prompt.getPromptString().trim() + " ");

        Result<T> result = prompt.transform(input);
        if (result == Result.EMPTY) {
          terminal.writer().println("Invalid.");
          terminal.writer().flush();
          continue;
        }
        return result;
      }
    } catch (UserInterruptException e) {
      return Result.userInterrupt();
    }

    return Result.empty();
  }

  @Override
  public PrintWriter getPrinter() {
    return terminal.writer();
  }
}
