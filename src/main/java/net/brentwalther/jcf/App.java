package net.brentwalther.jcf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import net.brentwalther.jcf.screen.MainMenuScreen;
import net.brentwalther.jcf.screen.SQLiteImportScreen;

import java.io.File;

public class App {

  @Parameter(
      names = {"--help", "-h"},
      help = true)
  private boolean help;

  @Parameter(names = {"--gnucash-sqlite-db"})
  private String sqliteDbFilePath;

  private static final String HELP_OPTION = "h";
  private static final String GNUCASH_DATABASE_FILE_OPTION = "gnucash-sqlite-db";

  public static void main(String[] args) throws Exception {
    App app = new App();
    JCommander.newBuilder().addObject(app).build().parse(args);
    app.run();
  }

  public void run() throws Exception {
    // Initialize the driver.
    // TODO: Figure out why this is necessary.
    Class.forName("org.sqlite.JDBC");

    File file = new File(sqliteDbFilePath);
    if (file.exists() && file.isFile()) {
      SQLiteImportScreen.start(file);
    }

    MainMenuScreen.start();
  }
}
