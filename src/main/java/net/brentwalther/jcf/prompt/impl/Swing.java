package net.brentwalther.jcf.prompt.impl;

import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.brentwalther.jcf.prompt.Prompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.prompt.PromptEvaluator;

public class Swing implements PromptEvaluator {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private static final ExecutorService SINGLE_THREADED_EXECUTOR_SERVICE =
      Executors.newSingleThreadExecutor();
  private static final Set<SwingPromptHandler> HANDLERS = Sets.newConcurrentHashSet();
  public Swing() {}

  public <T> void installPromptHandler(SwingPromptHandler handler) {
    HANDLERS.add(handler);
  }

  public <T> T showAndGetResult(Prompt prompt) {
    HANDLERS.forEach(handler -> {
      handler.handle(prompt);
    });
      String input = getInput(prompt);
      Optional<Result> result = prompt.transform(input);


//    for (SwingPromptHandler<?> handler : HANDLERS) {
//      SINGLE_THREADED_EXECUTOR_SERVICE.submit(
//          () ->
//              SwingUtilities.invokeLater(
//                  () -> {
//                    LOGGER.atInfo().log(
//                        "Handling swing prompt using handler %s",
//                        handler.getHandlerName());
//                    return handler.handle(prompt);
//                  }));
    }

  public interface SwingPromptHandler {
    String getHandlerName();

    void handle(Prompt prompt);
  }
  }
}
