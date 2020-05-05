package net.brentwalther.jcf.prompt;

import com.google.common.collect.ImmutableList;
import org.jline.terminal.Size;

import java.util.Optional;

/** A prompt to the user that returns some result of type {@code T}. */
public interface Prompt<T> {
  /**
   * Transforms the input to the result.
   *
   * @param input the user-provided input string
   * @return a present Optional of the type if the input could be used to construct it or
   *     Optional.empty() if it isn't a valid input.
   */
  Optional<T> transform(String input);

  /**
   * Called to allow the prompt to produce the instructions that should be shown above the prompt.
   *
   * @param size The number of row and columns you have to work with.
   */
  ImmutableList<String> getInstructions(Size size);

  /** Returns the string that should be printed before the user caret. */
  String getPromptString();

  /** Returns the list of status bar strings to printed at the very top of the screen. */
  ImmutableList<String> getStatusBars();
}
