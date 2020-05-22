package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import net.brentwalther.jcf.App;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.importer.OfxConnector;
import net.brentwalther.jcf.prompt.AccountPickerPrompt;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import org.jline.terminal.Terminal;

import java.io.File;

public class OFXImportScreen {

  public static void start(File file) {
    Terminal terminal = TerminalProvider.get();
    Model model = ModelManager.getCurrentModel();

    if (model.accountsById.isEmpty()) {
      showErrorMessages(
          terminal,
          ImmutableList.of("The current model has no accounts. There's nothing to match!"));
      return;
    }

    Model importedOfxModel = new OfxConnector(file).extract();

    if (importedOfxModel.splitsByTransactionId.isEmpty()) {
      System.err.println("No splits to match!");
      return;
    }

    if (importedOfxModel.accountsById.size() != 1) {
      showErrorMessages(
          terminal,
          ImmutableList.of(
              "Expected OFX importer to import exactly one account but found: "
                  + importedOfxModel.accountsById.size()
                  + "accounts"));
      return;
    }

    Account selectedAccount =
        PromptEvaluator.showAndGetResult(
            terminal,
            PromptDecorator.decorateWithStatusBars(
                AccountPickerPrompt.create(model.accountsById),
                ImmutableList.of(
                    "Picking account for import: "
                        + Iterables.getOnlyElement(importedOfxModel.accountsById.values()).name)));

    if (selectedAccount == null) {
      showErrorMessages(terminal, ImmutableList.of("No account selected. Aborting import."));
      return;
    }

    Model updatedModel =
        new Model(
            ImmutableList.of(selectedAccount),
            importedOfxModel.transactionsById.values().asList(),
            importedOfxModel.splitsByTransactionId.values().stream()
                .map(
                    (split) ->
                        new Split(
                            selectedAccount.id,
                            split.transactionId,
                            split.valueNumerator,
                            split.valueDenominator))
                .collect(ImmutableList.toImmutableList()));
    // Now that we know which account these transactions are for, fix the imported model and splits.
    ModelManager.addModel(updatedModel);
    PromptEvaluator.showAndGetResult(
        TerminalProvider.get(),
        NoticePrompt.withMessages(
            ImmutableList.of(
                "Imported " + updatedModel.accountsById.size() + " accounts.",
                "Imported " + updatedModel.transactionsById.size() + " transactions.",
                "Imported " + updatedModel.splitsByTransactionId.size() + " splits.")));
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
