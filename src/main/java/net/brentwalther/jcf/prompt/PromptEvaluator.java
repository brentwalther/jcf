package net.brentwalther.jcf.prompt;

import net.brentwalther.jcf.prompt.Prompt.Result;

public interface PromptEvaluator {
  Result<?> blockingGetResult(Prompt<?> prompt);
}
