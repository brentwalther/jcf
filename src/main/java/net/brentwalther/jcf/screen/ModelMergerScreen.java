package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.ModelManager;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;

public class ModelMergerScreen {
  public static void start(Model modelToMerge) {
    Model currentModel = ModelManager.getCurrentModel();
    ImmutableListMultimap<String, Split> currentSplitsByDescription =
        Multimaps.index(
            currentModel.splitsByTransactionId.values(),
            (split) ->
                currentModel.transactionsById.get(split.getTransactionId()).getDescription());

    for (Split split : modelToMerge.splitsByTransactionId.values()) {
      ImmutableList<Split> splitsWithSameDescription =
          currentSplitsByDescription.get(
              modelToMerge.transactionsById.get(split.getTransactionId()).getDescription());
      if (splitsWithSameDescription.isEmpty()) {
        continue;
      }
      for (Split otherSplit : splitsWithSameDescription) {
        Transaction transaction = modelToMerge.transactionsById.get(split.getTransactionId());
        Transaction otherTransaction =
            currentModel.transactionsById.get(otherSplit.getTransactionId());
        if (split.getAccountId().equals(otherSplit.getAccountId())
            && split.getValueNumerator() == otherSplit.getValueNumerator()
            && split.getValueDenominator() == otherSplit.getValueDenominator()
            && transaction.getPostDateEpochSecond() == otherTransaction.getPostDateEpochSecond()) {
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
