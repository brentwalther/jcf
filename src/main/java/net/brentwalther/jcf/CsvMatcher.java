package net.brentwalther.jcf;

import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import java.io.File;
import java.util.Optional;
import net.brentwalther.jcf.environment.JcfEnvironment;
import net.brentwalther.jcf.export.LedgerExporter;
import net.brentwalther.jcf.matcher.SplitMatcher;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.importer.CsvTransactionListingImporter;
import net.brentwalther.jcf.prompt.impl.TerminalPromptEvaluator;
import net.brentwalther.jcf.screen.SplitMatcherScreen;

public class CsvMatcher {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private static final String LEDGER_EXTENSION = "ledger";

  private final JcfEnvironment jcfEnvironment;

  public CsvMatcher(JcfEnvironment jcfEnvironment) {
    this.jcfEnvironment = jcfEnvironment;
  }

  public static void main(String[] args) {
    CsvMatcher csvMatcher =
        new CsvMatcher(
            JcfEnvironmentImpl.createFromArgsForEnv(args, TerminalPromptEvaluator.createOrDie()));
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

    SplitMatcher matcher = SplitMatcher.create(jcfEnvironment.getInitialModel());
    Model importedModelFromCsv = CsvTransactionListingImporter.create(jcfEnvironment).get();
    Model modelToExport =
        SplitMatcherScreen.start(
            jcfEnvironment.getPromptEvaluator(),
            matcher,
            /* modelToMatch= */ IndexedModel.create(importedModelFromCsv),
            /* allInitiallyKnownAccountsById= */ Maps.uniqueIndex(
                jcfEnvironment.getInitialModel().getAccountList(), Account::getId));

    String outputFilePath = outputFile.getAbsolutePath();
    int lastDotIndex = outputFilePath.lastIndexOf('.');
    String extension =
        lastDotIndex > 0 && lastDotIndex < outputFilePath.length() - 1
            ? outputFilePath.substring(outputFilePath.lastIndexOf('.') + 1)
            : "";
    boolean success = true;
    if (extension.equals(LEDGER_EXTENSION)) {
      success = LedgerExporter.exportToFile(IndexedModel.create(modelToExport), outputFile);
    } else {
      LOGGER.atWarning().log(
          "Unknown file extension %s on output file path %s. Writing a ledger CLI text file there.",
          extension, outputFilePath);
      success = LedgerExporter.exportToFile(IndexedModel.create(modelToExport), outputFile);
    }
    LOGGER.atInfo().log(
        "Wrote file: %s - %s", success ? "yes" : "no", outputFile.getAbsolutePath());
  }
}
