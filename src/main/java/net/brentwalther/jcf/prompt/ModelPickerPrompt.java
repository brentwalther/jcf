package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import net.brentwalther.jcf.model.IndexedModel;

public class ModelPickerPrompt implements Prompt<IndexedModel> {
  private final ImmutableMap<String, IndexedModel> modelsByIdentifier;
  private final OptionsPrompt optionsPrompt;

  private ModelPickerPrompt(ImmutableList<IndexedModel> modelGeneratorOptions) {
    this.modelsByIdentifier =
        Maps.uniqueIndex(modelGeneratorOptions, IndexedModel::stableIdentifier);
    this.optionsPrompt =
        OptionsPrompt.create(
            Lists.transform(modelGeneratorOptions, IndexedModel::stableIdentifier));
  }

  public static Prompt<IndexedModel> create(List<IndexedModel> modelGeneratorOptions) {
    return new ModelPickerPrompt(ImmutableList.copyOf(modelGeneratorOptions));
  }

  @Override
  public Result<IndexedModel> transform(String input) {
    Result<String> selectedOption = optionsPrompt.transform(input);
    // Assume the choice will always be a number.
    return selectedOption
        .instance()
        .map(modelsByIdentifier::get)
        .<Result<IndexedModel>>map(Result::model)
        .orElse(Result.empty());
  }

  @Override
  public ImmutableList<String> getInstructions(SizeBounds size) {
    return optionsPrompt.getInstructions(size);
  }

  @Override
  public String getPromptString() {
    return optionsPrompt.getPromptString();
  }

  @Override
  public ImmutableList<String> getStatusBars() {
    return optionsPrompt.getStatusBars();
  }

  @Override
  public ImmutableSet<String> getAutoCompleteOptions() {
    return ImmutableSet.of();
  }

  @Override
  public boolean shouldClearScreen() {
    return false;
  }
}
