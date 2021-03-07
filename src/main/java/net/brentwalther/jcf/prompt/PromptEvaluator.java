package net.brentwalther.jcf.prompt;

import net.brentwalther.jcf.prompt.Prompt.Result;

public interface PromptEvaluator {
  <T> Result<T> blockingGetResult(Prompt<T> prompt);
}
