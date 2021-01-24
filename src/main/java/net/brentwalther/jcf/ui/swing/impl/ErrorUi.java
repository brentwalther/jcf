package net.brentwalther.jcf.ui.swing.impl;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.awt.Container;
import javax.swing.JTextArea;
import net.brentwalther.jcf.ui.swing.SwingUi;

public class ErrorUi implements SwingUi {
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
}
