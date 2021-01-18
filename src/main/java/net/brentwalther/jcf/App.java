package net.brentwalther.jcf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import java.io.File;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.model.importer.SQLiteConnector;
import net.brentwalther.jcf.prompt.impl.TerminalPromptEvaluator;
import net.brentwalther.jcf.screen.MainMenuScreen;

public class App {

  @Parameter(names = {"--gnucash-sqlite-db"})
  private String sqliteDbFilePath = "";

  public static void main(String[] args) throws Exception {
    App app = new App();
    JCommander.newBuilder().addObject(app).build().parse(args);
    app.run();
  }

  private void run() throws Exception {
    JcfModel.Model model = ModelGenerators.empty();
    if (!sqliteDbFilePath.isEmpty()) {
      File file = new File(sqliteDbFilePath);
      if (file.exists() && file.isFile()) {
        model = SQLiteConnector.create(file).get();
      }
    }

    MainMenuScreen.start(TerminalPromptEvaluator.createOrDie(), model);
  }
}
