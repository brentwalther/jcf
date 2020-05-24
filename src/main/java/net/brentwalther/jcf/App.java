package net.brentwalther.jcf;

import net.brentwalther.jcf.screen.MainMenuScreen;
import net.brentwalther.jcf.screen.SQLiteImportScreen;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jline.terminal.Terminal;

import java.io.File;

public class App {

  private static final String HELP_OPTION = "h";
  private static final String GNUCASH_DATABASE_FILE_OPTION = "gnucash-sqlite-db";

  public static void main(String[] args) throws Exception {
    // Initialize the driver.
    // TODO: Figure out why this is necessary.
    Class.forName("org.sqlite.JDBC");

    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(getOptions(), args);

    Terminal terminal = TerminalProvider.get();

    if (commandLine.hasOption(HELP_OPTION)) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printOptions(
          terminal.writer(), terminal.getWidth(), getOptions(), /* leftPad= */ 0, /* descPad= */ 2);
      terminal.flush();
      System.exit(0);
    }

    if (commandLine.hasOption(GNUCASH_DATABASE_FILE_OPTION)) {
      String filePath = commandLine.getOptionValue(GNUCASH_DATABASE_FILE_OPTION);
      File file = new File(filePath);
      if (file.exists() && file.isFile()) {
        SQLiteImportScreen.start(file);
      }
    }

    MainMenuScreen.start();
  }

  private static Options getOptions() {
    return new Options()
        .addOption(new Option(HELP_OPTION, "help", /* hasArg= */ false, "Prints this help text"))
        .addOption(
            Option.builder()
                .longOpt(GNUCASH_DATABASE_FILE_OPTION)
                .hasArg(true)
                .numberOfArgs(1)
                .argName("file path")
                .desc("Path to the GnuCash sqlite DB to load at startup.")
                .build());
  }
}
