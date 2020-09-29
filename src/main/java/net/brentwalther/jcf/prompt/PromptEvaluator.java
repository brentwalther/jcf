package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.io.PrintWriter;
import java.util.Optional;

public class PromptEvaluator<T> {

  private static final String HORIZONTAL_LINE = Character.toString((char) 0x2500);

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
                .readLine("| " + prompt.getPromptString());
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
    Size instructionsSize = new Size(size.getColumns(), size.getRows() - statusBars.size() - 5);
    ImmutableList<String> instructions = prompt.getInstructions(instructionsSize);

    writer.print(duplicate(HORIZONTAL_LINE, size.getColumns()));
    if (!statusBars.isEmpty()) {
      statusBars.forEach(writer::println);
      writer.println(duplicate(HORIZONTAL_LINE, size.getColumns()));
    }
    instructions.forEach(writer::println);
    writer.println(duplicate(HORIZONTAL_LINE, size.getColumns()));
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
