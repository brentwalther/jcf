package net.brentwalther.jcf.testing;

import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.Prompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import org.hamcrest.Matcher;
import org.mockito.ArgumentMatcher;

public class ArgumentMatchers {
  public static final Matcher<Prompt<?>> IS_A_DECORATED_OPTIONS_PROMPT =
      new ArgumentMatcher<Prompt<?>>() {
        @Override
        public boolean matches(Object o) {
          return (o instanceof PromptDecorator<?>)
              && (((PromptDecorator<?>) o).delegate() instanceof OptionsPrompt);
        }
      };
}
