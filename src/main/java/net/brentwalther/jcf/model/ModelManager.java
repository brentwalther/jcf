package net.brentwalther.jcf.model;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.Map;

public class ModelManager {

  private static Model model = Model.empty();

  private static Map<String, Model> unmergedModels = new HashMap<>();

  public static void mergeModel(Model model) {
    Model currentModel = ModelManager.model;
    ModelManager.model =
        new Model(
            ImmutableList.<Account>builder()
                .addAll(currentModel.accountsById.values())
                .addAll(model.accountsById.values())
                .build(),
            ImmutableList.<Transaction>builder()
                .addAll(currentModel.transactionsById.values())
                .addAll(model.transactionsById.values())
                .build(),
            ImmutableList.<Split>builder()
                .addAll(currentModel.splitsByTransactionId.values())
                .addAll(model.splitsByTransactionId.values())
                .build());
  }

  public static Model getCurrentModel() {
    return model;
  }

  public static void addModel(Model model) {
    unmergedModels.put(model.id, model);
  }

  public static void removeModel(Model model) {
    unmergedModels.remove(model.id);
  }

  public static ImmutableList<Model> getUnmergedModels() {
    return ImmutableList.copyOf(unmergedModels.values());
  }
}
