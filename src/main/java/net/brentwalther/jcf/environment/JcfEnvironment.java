package net.brentwalther.jcf.environment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Function;
import net.brentwalther.jcf.SettingsProto.SettingsProfile.DataField;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.prompt.PromptEvaluator;

public interface JcfEnvironment {

  /** Returns the initial imported model - a merge of all parse-able inputs. */
  Model getInitialModel();

  /** Returns the output file that the user declared, if any. The file might be created already. */
  Optional<File> getDeclaredOutputFile();

  /**
   * A generator that yields the canonical account for a given string extracted during CSV import.
   * Note that the function is not required to use the input string.
   */
  Function<String, Account> getImportAccountGenerator();

  /** Returns the date format for the transactions in the input CSV file. */
  Optional<DateTimeFormatter> getCsvDateFormat();

  /** Returns the declared CSV file as a list of it's lines, if any. */
  ImmutableList<String> getInputCsvLines();

  /**
   * Returns the mapping from DataField type to column number (in the CSV input). It is up to the
   * caller to verify they are sufficient.
   */
  ImmutableMap<DataField, Integer> getCsvFieldMappings();

  /**
   * Returns true if the environment is in a bad state and wants to print help text via {@link
   * #printHelpTextTo(StringBuilder)}.
   */
  boolean needsHelp();

  /**
   * If {@link #needsHelp()} returns true, this method will print help text to the specified string
   * builder.
   */
  void printHelpTextTo(StringBuilder usageStringBuilder);

  /** Returns the prompt evaluator that should be used for the configured environment. */
  PromptEvaluator getPromptEvaluator();
}
