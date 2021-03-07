package net.brentwalther.jcf.prompt;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import net.brentwalther.jcf.model.IndexedModel;
import net.brentwalther.jcf.model.JcfModel.Account;

/** A prompt to the user for a result. */
public interface Prompt<T> {

  /**
   * Transforms the input to the result.
   *
   * @param input the user-provided input string
   * @return a present Optional of the type if the input could be used to construct it or
   *     Optional.empty() if it isn't a valid input.
   */
  Result<T> transform(String input);

  /**
   * Called to allow the prompt to produce the instructions that should be shown above the prompt.
   *
   * @param size The number of row and columns you have to work with.
   */
  ImmutableList<String> getInstructions(SizeBounds size);

  /** Returns the string that should be printed before the user caret. */
  String getPromptString();

  /** Returns the list of status bar strings to printed at the very top of the screen. */
  ImmutableList<String> getStatusBars();

  /** Returns the set of strings that the prompt auto completer will be filled with. */
  ImmutableSet<String> getAutoCompleteOptions();

  /** If true, the prompt evaluator will clear everything on the prompt screen before printing. */
  boolean shouldClearScreen();

  @AutoValue
  abstract class Result<T> {

    public static Result<Object> USER_INTERRUPT = create(Object.class, Optional.empty());

    public static Result<Object> EMPTY = create(Object.class, Optional.empty());

    // Casting to any type is safe because the optional is empty.
    @SuppressWarnings("unchecked")
    public static <T> Result<T> empty() {
      return (Result<T>) EMPTY;
    }

    // Casting to any type is safe because the optional is empty.
    @SuppressWarnings("unchecked")
    public static <T> Result<T> userInterrupt() {
      return (Result<T>) USER_INTERRUPT;
    }

    public static Result<File> file(File file) {
      return create(File.class, Optional.of(file));
    }

    public static Result<Account> account(Account account) {
      return create(Account.class, Optional.of(account));
    }

    public static Result<IndexedModel> model(IndexedModel indexedModel) {
      return create(IndexedModel.class, Optional.of(indexedModel));
    }

    public static Result<String> string(String s) {
      return create(String.class, Optional.of(s));
    }

    public static Result<DateTimeFormatter> dateTimeFormatter(DateTimeFormatter formatter) {
      return create(DateTimeFormatter.class, Optional.of(formatter));
    }

    public static Result<BigDecimal> bigDecimal(BigDecimal bigDecimal) {
      return create(BigDecimal.class, Optional.of(bigDecimal));
    }

    private static <TT> Result<TT> create(Class<TT> type, Optional<TT> instance) {
      Preconditions.checkState(!instance.isPresent() || type.isInstance(instance.get()));
      return new AutoValue_Prompt_Result<>(instance);
    }

    public abstract Optional<T> instance();
  }
}
