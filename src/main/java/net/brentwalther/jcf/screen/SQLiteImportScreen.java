package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.model.importer.SQLiteConnector;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;

import java.io.File;

public class SQLiteImportScreen {
  public static void start(File file) {
    Model model = new SQLiteConnector(file).extract();

    PromptEvaluator.showAndGetResult(
        TerminalProvider.get(),
        NoticePrompt.withMessages(
            ImmutableList.of(
                "Imported " + model.accountsById.size() + " accounts.",
                "Imported " + model.transactionsById.size() + " transactions.",
                "Imported " + model.splitsByTransactionId.size() + " splits.")));

    ModelManager.mergeModel(model);
  }
}
