package net.brentwalther.jcf.ui.swing;

import java.awt.Container;
import net.brentwalther.jcf.prompt.PromptEvaluator;

public interface SwingUi extends PromptEvaluator {
  /** A reference to the root container for the UI. */
  Container getRootContainer();
}
