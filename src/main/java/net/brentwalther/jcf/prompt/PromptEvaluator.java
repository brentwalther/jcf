package net.brentwalther.jcf.prompt;

import java.io.PrintWriter;
import net.brentwalther.jcf.prompt.Prompt.Result;

public interface PromptEvaluator {
  <T> Result<T> blockingGetResult(Prompt<T> prompt);

  PrintWriter getPrinter();

  enum Type {
    UNKNOWN,
    TERMINAL,
    SWING_UI,
  }
}
