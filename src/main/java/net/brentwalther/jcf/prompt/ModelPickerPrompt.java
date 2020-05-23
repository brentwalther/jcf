package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import net.brentwalther.jcf.model.Model;
import org.jline.terminal.Size;

import java.util.Optional;

public class ModelPickerPrompt implements Prompt<Model> {
  private final OptionsPrompt optionsPrompt;
  private final ImmutableList<Model> modelOptions;

  private ModelPickerPrompt(ImmutableList<Model> modelOptions) {
    this.optionsPrompt = OptionsPrompt.create(Lists.transform(modelOptions, Model::toString));
    this.modelOptions = modelOptions;
  }

  public static Prompt<Model> create(ImmutableList<Model> modelOptions) {
    return new ModelPickerPrompt(modelOptions);
  }

  @Override
  public Optional<Model> transform(String input) {
    Optional<OptionsPrompt.Choice> selectedOption = optionsPrompt.transform(input);
    if (selectedOption.isPresent() && selectedOption.get().type != OptionsPrompt.ChoiceType.EMPTY) {
      // Assume the choice will always be a number.
      return Optional.of(modelOptions.get(selectedOption.get().numberChoice));
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
}
