package net.brentwalther.jcf;

import com.google.common.flogger.FluentLogger;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.importer.CsvTransactionListingImporter;
import net.brentwalther.jcf.screen.LedgerExportScreen;
import net.brentwalther.jcf.screen.SplitMatcherScreen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Optional;

public class CsvMatcher {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private static final String LEDGER_EXTENSION = "ledger";

  private final JcfEnvironment jcfEnvironment;

  public CsvMatcher(JcfEnvironment jcfEnvironment) {
    this.jcfEnvironment = jcfEnvironment;
  }

  public static void main(String[] args) {
    CsvMatcher csvMatcher = new CsvMatcher(JcfEnvironment.createFromArgs(args));
    csvMatcher.run();
  }

  private void run() {
    if (jcfEnvironment.needsHelp()) {
      StringBuilder usageStringBuilder = new StringBuilder();
      usageStringBuilder.append("CSV Matcher needs help. Printing help text and exiting...\n");
      jcfEnvironment.printHelpTextTo(usageStringBuilder);
      System.out.print(usageStringBuilder.toString());
      System.exit(1);
    }

    Optional<File> maybeOutputFile = jcfEnvironment.getDeclaredOutputFile();

    if (!maybeOutputFile.isPresent()) {
      LOGGER.atSevere().log(
          "You must specify an output file that doesn't already exist. See --help for help.");
      return;
    }
    File outputFile = maybeOutputFile.get();

    SplitMatcher matcher = SplitMatcher.create(jcfEnvironment.initialModel());
    Model importedModelFromCsv = CsvTransactionListingImporter.create(jcfEnvironment).get();
    Model modelToExport =
        SplitMatcherScreen.start(
            matcher,
            /* modelToMatch= */ IndexedModel.create(importedModelFromCsv),
            /* allKnownAccounts= */ jcfEnvironment.initialModel().getAccountList());

    String outputFilePath = outputFile.getAbsolutePath();
    int lastDotIndex = outputFilePath.lastIndexOf('.');
    String extension =
        lastDotIndex > 0 && lastDotIndex < outputFilePath.length() - 1
            ? outputFilePath.substring(outputFilePath.lastIndexOf('.') + 1)
            : "";
    try {
      if (extension.equals(LEDGER_EXTENSION)) {
        LedgerExportScreen.start(
            IndexedModel.create(modelToExport), new FileOutputStream(outputFile));
      } else {
        LOGGER.atWarning().log(
            "Unknown file extension %s on output file path %s. Writing a ledger CLI text file there.",
            extension, outputFilePath);
        LedgerExportScreen.start(
            IndexedModel.create(modelToExport), new FileOutputStream(outputFile));
      }
    } catch (FileNotFoundException e) {
      LOGGER.atSevere().log("File could not be opened: %s", outputFile.getAbsolutePath());
    }
  }
}
