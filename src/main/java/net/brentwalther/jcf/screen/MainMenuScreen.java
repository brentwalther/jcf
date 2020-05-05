package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.brentwalther.jcf.App;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.prompt.FilePrompt;
import net.brentwalther.jcf.prompt.ModelPickerPrompt;
import net.brentwalther.jcf.prompt.OptionsPrompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import org.jline.terminal.Terminal;

import java.io.File;

public class MainMenuScreen {

  private static final ImmutableMap<String, Screen> MAIN_MENU_OPTIONS =
      ImmutableMap.<String, Screen>builder()
          .put("Load GnuCash SQLite DB", Screen.LOAD_SQLITE)
          .put("Load OFX file", Screen.LOAD_OFX)
          .put("Review Unmerged Model(s)", Screen.REVIEW_UNMERGED_MODELS)
          .put("Export all current model expenses", Screen.CSV_EXPORT)
          .put("Exit application", Screen.EXIT)
          .build();

  public static void start() {
    Terminal terminal = App.getTerminal();
    ImmutableList<String> options = MAIN_MENU_OPTIONS.keySet().asList();

    while (true) {
      ImmutableList<String> statusBars =
          ImmutableList.of(
              "Current model: " + ModelManager.getCurrentModel().toString(),
              "Unmerged imports: " + ModelManager.getUnmergedModels().size());
      Integer selectedOption =
          PromptEvaluator.showAndGetResult(
              terminal,
              PromptDecorator.decorateWithStatusBars(OptionsPrompt.create(options), statusBars));
      if (selectedOption == null) {
        break;
      }
      Screen nextState = MAIN_MENU_OPTIONS.get(options.get(selectedOption));
      switch (nextState) {
        case LOAD_SQLITE:
          File sqliteFile = PromptEvaluator.showAndGetResult(terminal, FilePrompt.existingFile());
          if (sqliteFile != null) {
            SQLiteImportScreen.start(sqliteFile);
          }
          break;
        case LOAD_OFX:
          File ofxFile = PromptEvaluator.showAndGetResult(terminal, FilePrompt.existingFile());
          if (ofxFile != null) {
            OFXImportScreen.start(ofxFile);
          }
          break;
        case REVIEW_UNMERGED_MODELS:
          Model modelToReview =
              PromptEvaluator.showAndGetResult(
                  terminal, ModelPickerPrompt.create(ModelManager.getUnmergedModels()));
          if (modelToReview != null) {
            ModelReviewScreen.start(modelToReview);
          }
          break;
        case CSV_EXPORT:
          File csvFile = PromptEvaluator.showAndGetResult(terminal, FilePrompt.anyFile());
          if (csvFile != null && csvFile.exists()) {
            CsvExportScreen.start(
                ModelManager.getCurrentModel(),
                csvFile,
                /* filters= */ ImmutableList.of(
                    exportItem -> !exportItem.account().type.equals(Account.Type.EXPENSE)));
          }
          break;
        case EXIT:
          return;
      }
    }
  }
}
