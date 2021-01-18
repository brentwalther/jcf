package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.brentwalther.jcf.export.CsvExporter;
import net.brentwalther.jcf.export.LedgerExporter;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.model.importer.SQLiteConnector;
import net.brentwalther.jcf.prompt.FilePrompt;
import net.brentwalther.jcf.prompt.ModelPickerPrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;

public class MainMenuScreen {

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

  private static final ImmutableMap<String, Screen> MAIN_MENU_OPTIONS =
      ImmutableMap.<String, Screen>builder()
          .put("Load GnuCash SQLite DB", Screen.LOAD_SQLITE)
          .put("Load OFX file", Screen.LOAD_OFX)
          .put("Review Unmerged Model(s)", Screen.REVIEW_UNMERGED_MODELS)
          .put("Export all current model expenses", Screen.CSV_EXPORT)
          .put("Export current model to ledger format", Screen.LEDGER_EXPORT)
          .put("Exit application", Screen.EXIT)
          .build();

  public static void start(PromptEvaluator promptEvaluator, JcfModel.Model initialModel) {
    Model currentModel = initialModel;
    List<IndexedModel> unmergedModels = new ArrayList<>();
    while (true) {
      ImmutableList<String> statusBars =
          ImmutableList.of("Unmerged imports: " + unmergedModels.size());
      Result result =
          promptEvaluator.blockingGetResult(
              PromptDecorator.topStatusBars(
                  OptionsPrompt.create(MAIN_MENU_OPTIONS.keySet().asList()), statusBars));

      if (result == null || !result.instance().isPresent()) {
        break;
      }

      Screen nextState = MAIN_MENU_OPTIONS.get(result.instance().get());
      if (nextState == null) {
        break;
      }
      switch (nextState) {
        case LOAD_SQLITE:
          result = promptEvaluator.blockingGetResult(FilePrompt.existingFile());
          if (result.instance().isPresent()) {
            File file = (File) result.instance().get();
            currentModel =
                ModelGenerators.merge(SQLiteConnector.create(file).get()).into(currentModel);
          }
          break;
        case LOAD_OFX:
          result = promptEvaluator.blockingGetResult(FilePrompt.existingFile());
          if (result.instance().isPresent()) {
            File file = (File) result.instance().get();
            unmergedModels.add(IndexedModel.create(OFXImportScreen.start(promptEvaluator, file)));
          }
          break;
        case REVIEW_UNMERGED_MODELS:
          result = promptEvaluator.blockingGetResult(ModelPickerPrompt.create(unmergedModels));
          if (result.instance().isPresent()) {
            IndexedModel modelToReview = (IndexedModel) result.instance().get();
            unmergedModels.remove(modelToReview);
            unmergedModels.add(ModelReviewScreen.start(promptEvaluator, modelToReview));
          }
          break;
        case CSV_EXPORT:
          result = promptEvaluator.blockingGetResult(FilePrompt.existingFile());
          if (result.instance().isPresent()) {
            File csvFile = (File) result.instance().get();
            if (csvFile.exists()) {
              LOGGER.atWarning().log(
                  "CSV file at %s already exists. Not overwriting it.", csvFile.getAbsolutePath());
              break;
            }
            CsvExporter.start(
                IndexedModel.create(currentModel),
                csvFile,
                /* filters= */ ImmutableList.of(
                    exportItem -> !exportItem.account().getType().equals(Account.Type.EXPENSE)));
          }
          break;
        case LEDGER_EXPORT:
          result = promptEvaluator.blockingGetResult(FilePrompt.existingFile());
          if (result.instance().isPresent()) {
            File ledgerFile = (File) result.instance().get();
            if (ledgerFile.exists()) {
              LOGGER.atWarning().log(
                  "Ledger file at %s already exists. Not overwriting it.",
                  ledgerFile.getAbsolutePath());
              break;
            }
            LedgerExporter.exportToFile(IndexedModel.create(currentModel), ledgerFile);
          }
        case EXIT:
          return;
      }
    }
  }
}
