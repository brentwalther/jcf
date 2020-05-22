package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import net.brentwalther.jcf.App;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;

public class ModelMergerScreen {
  public static void start(Model modelToMerge) {
    Model currentModel = ModelManager.getCurrentModel();
    ImmutableListMultimap<String, Split> currentSplitsByDescription =
        Multimaps.index(
            currentModel.splitsByTransactionId.values(),
            (split) -> currentModel.transactionsById.get(split.transactionId).description);

    for (Split split : modelToMerge.splitsByTransactionId.values()) {
      ImmutableList<Split> splitsWithSameDescription =
          currentSplitsByDescription.get(
              modelToMerge.transactionsById.get(split.transactionId).description);
      if (splitsWithSameDescription.isEmpty()) {
        continue;
      }
      for (Split otherSplit : splitsWithSameDescription) {
        Transaction transaction = modelToMerge.transactionsById.get(split.transactionId);
        Transaction otherTransaction = currentModel.transactionsById.get(otherSplit.transactionId);
        if (split.accountId.equals(otherSplit.accountId)
            && split.amount().compareTo(otherSplit.amount()) == 0
            && transaction.postDate.equals(otherTransaction.postDate)) {
          PromptEvaluator.showAndGetResult(
              TerminalProvider.get(),
              NoticePrompt.withMessages(
                  ImmutableList.of(
                      "This transaction appears to be a duplicate. Refusing to merge!",
                      transaction.toString() + " with split " + split.toString())));
          return;
        }
      }
      ModelManager.mergeModel(modelToMerge);
      ModelManager.removeModel(modelToMerge);
    }
  }
}
