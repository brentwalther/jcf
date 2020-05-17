package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.stream.IntStream;

public class PromptEvaluator<T> {

  private static final String HORIZONTAL_LINE = Character.toString((char) 0x2500);

  public static <T> T showAndGetResult(Terminal terminal, Prompt<T> prompt) {
    Optional<T> result = Optional.empty();
    do {
      terminal.puts(InfoCmp.Capability.clear_screen);
      Size size = terminal.getSize();
      printInstructions(prompt, terminal.writer(), size);
      terminal.flush();
      String input;
      try {
        input = new LineReaderImpl(terminal).readLine(prompt.getPromptString());
      } catch (IOException | UserInterruptException e) {
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
    Size instructionsSize = new Size(size.getColumns(), size.getRows() - statusBars.size() - 2);
    ImmutableList<String> instructions = prompt.getInstructions(instructionsSize);

    statusBars.forEach(writer::println);
    writer.println(duplicate(HORIZONTAL_LINE, size.getColumns()));
    IntStream.range(0, instructionsSize.getRows() - instructions.size())
        .forEach((i) -> writer.println());
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
