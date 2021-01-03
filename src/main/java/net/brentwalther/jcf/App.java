package net.brentwalther.jcf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.model.importer.SQLiteConnector;
import net.brentwalther.jcf.screen.MainMenuScreen;

import java.io.File;

public class App {

  private static final String HELP_OPTION = "h";
  private static final String GNUCASH_DATABASE_FILE_OPTION = "gnucash-sqlite-db";

  @Parameter(
      names = {"--help", "-h"},
      help = true)
  private boolean help;

  @Parameter(names = {"--gnucash-sqlite-db"})
  private String sqliteDbFilePath;

  public static void main(String[] args) throws Exception {
    App app = new App();
    JCommander.newBuilder().addObject(app).build().parse(args);
    app.run();
  }

  private void run() throws Exception {
    // Initialize the driver.
    // TODO: Figure out why this is necessary.
    Class.forName("org.sqlite.JDBC");

    File file = new File(sqliteDbFilePath);
    JcfModel.Model model = ModelGenerators.empty();
    if (file.exists() && file.isFile()) {
      model = SQLiteConnector.create(file).get();
    }

    MainMenuScreen.start(model);
  }
}
