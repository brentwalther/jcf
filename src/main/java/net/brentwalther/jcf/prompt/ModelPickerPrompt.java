package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import net.brentwalther.jcf.model.IndexedModel;
import org.jline.terminal.Size;

import java.util.List;
import java.util.Optional;

public class ModelPickerPrompt implements Prompt<IndexedModel> {
  private final OptionsPrompt optionsPrompt;
  private final ImmutableList<IndexedModel> modelGeneratorOptions;

  private ModelPickerPrompt(ImmutableList<IndexedModel> modelGeneratorOptions) {
    this.optionsPrompt =
        OptionsPrompt.create(
            Lists.transform(modelGeneratorOptions, IndexedModel::stableIdentifier));
    this.modelGeneratorOptions = modelGeneratorOptions;
  }

  public static Prompt<IndexedModel> create(List<IndexedModel> modelGeneratorOptions) {
    return new ModelPickerPrompt(ImmutableList.copyOf(modelGeneratorOptions));
  }

  @Override
  public Optional<IndexedModel> transform(String input) {
    Optional<OptionsPrompt.Choice> selectedOption = optionsPrompt.transform(input);
    if (selectedOption.isPresent() && selectedOption.get().type != OptionsPrompt.ChoiceType.EMPTY) {
      // Assume the choice will always be a number.
      return Optional.of(modelGeneratorOptions.get(selectedOption.get().numberChoice));
    }
    return Optional.empty();
  }

  @Override
  public ImmutableList<String> getInstructions(Size size) {
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
