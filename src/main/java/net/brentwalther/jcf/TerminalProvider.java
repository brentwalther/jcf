package net.brentwalther.jcf;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

public class TerminalProvider {
  private static Terminal instance;

  public static Terminal get() {
    synchronized (TerminalProvider.class) {
      if (instance == null) {
        try {
          instance = TerminalBuilder.terminal();
        } catch (IOException e) {
          System.err.println("Could not initialize terminal: " + e);
          System.exit(1);
        }
      }
      return instance;
    }
  }
}
