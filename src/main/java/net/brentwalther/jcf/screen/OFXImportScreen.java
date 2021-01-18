package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import java.io.File;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.importer.OfxConnector;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;

public class OFXImportScreen {

  public static JcfModel.Model start(PromptEvaluator promptEvaluator, File file) {
    JcfModel.Model importedOfxModel = new OfxConnector(file).get();

    if (importedOfxModel.getSplitCount() == 0) {
      System.err.println("No splits to match!");
      return JcfModel.Model.getDefaultInstance();
    }

    if (importedOfxModel.getAccountCount() != 1) {
      showErrorMessages(
          promptEvaluator,
          ImmutableList.of(
              "Expected OFX importer to import exactly one account but found: "
                  + importedOfxModel.getAccountCount()
                  + "accounts"));
      return JcfModel.Model.getDefaultInstance();
    }

    promptEvaluator.blockingGetResult(
        NoticePrompt.withMessages(
            ImmutableList.of(
                "Imported " + importedOfxModel.getAccountCount() + " accounts.",
                "Imported " + importedOfxModel.getTransactionCount() + " transactions.",
                "Imported " + importedOfxModel.getSplitCount() + " splits.")));

    return importedOfxModel;
  }

  private static void showErrorMessages(
      PromptEvaluator promptEvaluator, ImmutableList<String> errors) {
    promptEvaluator.blockingGetResult(
        NoticePrompt.withMessages(
            ImmutableList.<String>builderWithExpectedSize(errors.size() + 1)
                .add("Error!")
                .addAll(errors)
                .build()));
  }
}
