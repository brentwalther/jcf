package net.brentwalther.jcf;

import net.brentwalther.jcf.environment.JcfEnvironment;
import net.brentwalther.jcf.prompt.impl.TerminalPromptEvaluator;
import net.brentwalther.jcf.screen.MainMenuScreen;

public class App {

  public static void main(String[] args) throws Exception {
    App app = new App();
    JcfEnvironment environment =
        JcfEnvironmentImpl.createFromArgsForEnv(args, TerminalPromptEvaluator.createOrDie());
    app.run(environment);
  }

  private void run(JcfEnvironment environment) throws Exception {
    MainMenuScreen.start(environment.getPromptEvaluator(), environment.getInitialModel());
  }
}
