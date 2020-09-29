package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.importer.OfxConnector;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import org.jline.terminal.Terminal;

import java.io.File;

public class OFXImportScreen {

  public static JcfModel.Model start(File file) {
    Terminal terminal = TerminalProvider.get();

    JcfModel.Model importedOfxModel = new OfxConnector(file).get();

    if (importedOfxModel.getSplitCount() == 0) {
      System.err.println("No splits to match!");
      return JcfModel.Model.getDefaultInstance();
    }

    if (importedOfxModel.getAccountCount() != 1) {
      showErrorMessages(
          terminal,
          ImmutableList.of(
              "Expected OFX importer to import exactly one account but found: "
                  + importedOfxModel.getAccountCount()
                  + "accounts"));
      return JcfModel.Model.getDefaultInstance();
    }

    PromptEvaluator.showAndGetResult(
        TerminalProvider.get(),
        NoticePrompt.withMessages(
            ImmutableList.of(
                "Imported " + importedOfxModel.getAccountCount() + " accounts.",
                "Imported " + importedOfxModel.getTransactionCount() + " transactions.",
                "Imported " + importedOfxModel.getSplitCount() + " splits.")));

    return importedOfxModel;
  }

  private static void showErrorMessages(Terminal terminal, ImmutableList<String> errors) {
    PromptEvaluator.showAndGetResult(
        terminal,
        NoticePrompt.withMessages(
            ImmutableList.<String>builderWithExpectedSize(errors.size() + 1)
                .add("Error!")
                .addAll(errors)
                .build()));
  }
}
