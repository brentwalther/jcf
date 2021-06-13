package net.brentwalther.jcf.ui.swing.impl;

import java.awt.Container;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.brentwalther.jcf.prompt.Prompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.ui.swing.SwingUi;

public class SwingUiImpl implements SwingUi {

  private final Container rootContainer;
  private final JTextArea textArea;
  private final ConcurrentLinkedQueue<String> pendingStringsToFlush = new ConcurrentLinkedQueue<>();

  public SwingUiImpl() {
    textArea = new JTextArea();
    textArea.setEditable(false);
    this.rootContainer = new JScrollPane(textArea);
  }

  public static SwingUi create() {
    return new SwingUiImpl();
  }

  public static SwingUi dummyAppFromHelpText(String string) {
    return new net.brentwalther.jcf.ui.swing.impl.ErrorUi(string);
  }

  @Override
  public Container getRootContainer() {
    return rootContainer;
  }

  @Override
  public <T> Result<T> blockingGetResult(Prompt<T> prompt) {
    // Don't do anything right now.
    return Result.empty();
  }

  @Override
  public PrintWriter getPrinter() {
    return new PrintWriter(
        new Writer() {
          @Override
          public void write(char[] chars, int off, int len) throws IOException {
            pendingStringsToFlush.add(new String(chars, off, len));
            SwingUtilities.invokeLater(
                () -> {
                  List<String> stringsToFlush = new ArrayList<>(pendingStringsToFlush.size());
                  String pendingString = pendingStringsToFlush.poll();
                  while (pendingString != null) {
                    stringsToFlush.add(pendingString);
                    pendingString = pendingStringsToFlush.poll();
                  }
                  int longestString =
                      stringsToFlush.stream().mapToInt(String::length).max().orElse(0);

                  textArea.setColumns(Math.max(textArea.getColumns(), longestString));
                  textArea.setRows(textArea.getRows() + stringsToFlush.size());
                  stringsToFlush.forEach(textArea::append);
                });
          }

          @Override
          public void flush() throws IOException {}

          @Override
          public void close() throws IOException {
            // Since the client of the writer is finished, go ahead and clear the area.
            textArea.setText("");
          }
        });
  }
}
