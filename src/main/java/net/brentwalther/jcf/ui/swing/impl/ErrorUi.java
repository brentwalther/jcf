package net.brentwalther.jcf.ui.swing.impl;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import java.awt.Container;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import javax.swing.JTextArea;
import net.brentwalther.jcf.prompt.Prompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.ui.swing.SwingUi;

public class ErrorUi implements SwingUi {
  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private final Supplier<Container> lazyRootContainer;

  public ErrorUi(String string) {
    this.lazyRootContainer =
        Suppliers.memoize(
            () -> {
              JTextArea textArea = new JTextArea();
              textArea.append(string);
              return textArea;
            });
  }

  @Override
  public Container getRootContainer() {
    return lazyRootContainer.get();
  }

  @Override
  public <T> Result<T> blockingGetResult(Prompt<T> prompt) {
    return Result.empty();
  }

  @Override
  public PrintWriter getPrinter() {
    return new PrintWriter(
        new Writer() {
          @Override
          public void write(char[] chars, int i, int i1) throws IOException {
            LOGGER.atSevere().log(
                "Application wanted to print text but ErrorUi doesn't allow appending. Dropping text:\n%s",
                new String(chars, i, i1));
          }

          @Override
          public void flush() throws IOException {}

          @Override
          public void close() throws IOException {}
        });
  }
}
