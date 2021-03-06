package net.brentwalther.jcf;

import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Verify;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.brentwalther.jcf.SettingsProto.SettingsProfile;
import net.brentwalther.jcf.SettingsProto.SettingsProfile.DataField;
import net.brentwalther.jcf.SettingsProto.SettingsProfiles;
import net.brentwalther.jcf.environment.JcfEnvironment;
import net.brentwalther.jcf.flag.CsvSetFlag;
import net.brentwalther.jcf.flag.DataFieldExtractor;
import net.brentwalther.jcf.flag.JcfEnvironmentFlagFactory;
import net.brentwalther.jcf.flag.NonExistentFile;
import net.brentwalther.jcf.flag.TextFileToLinesConverter.EagerlyLoadedTextFile;
import net.brentwalther.jcf.model.FileType;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.model.importer.CsvTransactionListingImporter;
import net.brentwalther.jcf.model.importer.LedgerFileImporter;
import net.brentwalther.jcf.model.importer.SQLiteConnector;
import net.brentwalther.jcf.model.importer.TsvTransactionDescAccountMappingImporter;
import net.brentwalther.jcf.prompt.DateTimeFormatPrompt;
import net.brentwalther.jcf.prompt.Prompt.Result;
import net.brentwalther.jcf.prompt.PromptEvaluator;

public class JcfEnvironmentImpl implements JcfEnvironment {

  /** The empty string, representing an unset string flag value. */
  private static final String UNSET_FLAG = "";

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private static final ImmutableMap<Command, Predicate<JcfEnvironment>>
      IS_ENVIRONMENT_SATISFACTORY_PREDICATE_BY_COMMAND =
          Maps.immutableEnumMap(
              ImmutableMap.of(
                  Command.CSV_MATCHER,
                  (env) ->
                      CsvTransactionListingImporter.isAcceptableFieldMappingSet(
                              env.getCsvFieldMappings().keySet())
                          && env.getCsvDateFormat().isPresent()
                          && env.getDeclaredOutputFile().isPresent()
                          && !env.getInputCsvLines().isEmpty(),
                  Command.GENERATE_REPORT,
                  (env) -> !env.getReportType().isEmpty()));
  private final PromptEvaluator promptEvaluator;
  private final Supplier<JCommander> lazyCommandLineParser =
      Suppliers.memoize(
          () ->
              JCommander.newBuilder()
                  .addObject(this)
                  .addConverterFactory(JcfEnvironmentFlagFactory.INSTANCE)
                  .build());

  @Parameter(description = "Main argument.")
  private String mainArgument = UNSET_FLAG;

  @Parameter(
      names = {"--ledger_account_listing"},
      description =
          "Optional. The file path to a ledger CLI format account listing file. A ledger CLI account "
              + "listing file is a file with lines of the format: account Account:Name")
  private EagerlyLoadedTextFile ledgerAccountListing = EagerlyLoadedTextFile.EMPTY;

  @Parameter(
      names = {"--master_ledger"},
      description = "Optional. The file path to a ledger CLI format master ledger file.")
  private EagerlyLoadedTextFile masterLedger = EagerlyLoadedTextFile.EMPTY;

  @Parameter(
      names = {"--transaction_csv"},
      description =
          "Required. The file path to a CSV format file which is a list of transactions from a single account, column names included.")
  private EagerlyLoadedTextFile inputCsv = EagerlyLoadedTextFile.EMPTY;

  @Parameter(
      names = {"--gnucash-sqlite-db"},
      description =
          "Optional. The file path to a GnuCash SQLite DB from which to read a model from.")
  private String gnuCashSqliteDbFilePath = UNSET_FLAG;

  @Parameter(
      names = {"--settings_profile_file"},
      description =
          "Optional. A comma separated list of paths to SettingProfiles textproto format files to apply to this run. "
              + "Named profiles are not applied by default and can be enabled using the --enable_profiles flag.",
      converter = CsvSetFlag.class)
  private Set<String> settingsProfileFiles = ImmutableSet.of();

  @Parameter(
      names = {"--enabled_settings_profiles"},
      description =
          "Optional. Names of named settings profiles that should be enabled. "
              + "Must be used in combination with --settings_profile_file flag to see effect.",
      converter = CsvSetFlag.class)
  private Set<String> enabledProfiles = ImmutableSet.of();

  @Parameter(
      names = {"--tsv_desc_account_mapping"},
      description =
          "Optional. A file path to a TSV file containing two columns: (1) transaction description (2) account name")
  private EagerlyLoadedTextFile descToAccountTsv = EagerlyLoadedTextFile.EMPTY;

  @Parameter(
      names = {"--output"},
      description = "Required. Path to a file to output to. It should not already exist.",
      converter = NonExistentFile.class)
  private Optional<File> outputFile = Optional.empty();

  @Parameter(
      names = {"--help", "-h"},
      description = "Print this help text.",
      help = true)
  private boolean userWantsHelp = false;

  @Parameter(
      names = {"--csv_field_ordering"},
      description =
          "Optional. A comma-separated field ordering for the columns in the CSV file (--transaction_csv).",
      converter = DataFieldExtractor.class)
  private ImmutableMap<DataField, Integer> csvFieldMapping = ImmutableMap.of();

  @Parameter(
      names = {"--csv_date_format"},
      description =
          "Optional. The java.time.format.DateTimeFormatter compatible date format string for the date column in the CSV file (--transaction_csv).")
  private String dateFormat = UNSET_FLAG;

  @Parameter(
      names = {"--csv_account_name", "--import_account_name"},
      description =
          "Optional. The fully qualified name of the account for which the transactions in the CSV file "
              + "(--transaction_csv) are coming from.")
  private String csvAccountName = UNSET_FLAG;

  @Parameter(
      names = {"--import_account_name_prefix"},
      description =
          "Optional. If set, the import account will be generated on-the-fly as a concatenation of this "
              + "prefix and the value extracted from the import with data type ACCOUNT_IDENTIFIER defined "
              + "in the settings profile.")
  private String importAccountPrefix = UNSET_FLAG;

  @Parameter(
      names = {"--report_type"},
      description =
          "Required when command is 'generate_report'. The report type to generate and output.")
  private String reportType = UNSET_FLAG;

  private final Supplier<Model> initialModelSupplier =
      Suppliers.memoize(
          () -> {
            Model model = ModelGenerators.empty();
            if (!gnuCashSqliteDbFilePath.isEmpty()) {
              File file = new File(gnuCashSqliteDbFilePath);
              if (file.exists() && file.isFile()) {
                model = SQLiteConnector.create(file).get();
              } else {
                LOGGER.atWarning().log(
                    "GNU Cash SQLite DB path did not refer to a file that exists. Path was: %s",
                    gnuCashSqliteDbFilePath);
              }
            }
            // This is a custom format that I added early on instead of just writing a ledger CLI
            // format importer. It's probably just useless cruft now but may as well keep it since
            // it's not broken as far as I am aware.
            if (!descToAccountTsv.lines().isEmpty()) {
              model =
                  ModelGenerators.merge(
                          extractModelFrom(
                              FileType.TSV_TRANSACTION_DESCRIPTION_TO_ACCOUNT_NAME_MAPPING,
                              descToAccountTsv.lines()))
                      .into(model);
            }
            if (!ledgerAccountListing.lines().isEmpty()) {
              JcfModel.Model allAccounts =
                  extractModelFrom(FileType.LEDGER_ACCOUNT_LISTING, ledgerAccountListing.lines());
              model = ModelGenerators.merge(allAccounts).into(model);
            }
            if (!masterLedger.lines().isEmpty()) {
              model =
                  ModelGenerators.merge(extractModelFrom(FileType.LEDGER_CLI, masterLedger.lines()))
                      .into(model);
            }
            LOGGER.atInfo().log(
                "Generated the initial model containing %s accounts and %s transactions.",
                model.getAccountCount(), model.getTransactionCount());
            return model;
          });

  private JcfEnvironmentImpl(PromptEvaluator promptEvaluator) {
    this.promptEvaluator = promptEvaluator;
  }

  public static JcfEnvironment createFromArgsForEnv(
      String[] args, PromptEvaluator promptEvaluator) {
    JcfEnvironmentImpl context = new JcfEnvironmentImpl(promptEvaluator);
    // Will initialize local @Parameter flags.
    context.lazyCommandLineParser.get().parse(args);
    context.applySettingsProfiles();
    return context;
  }

  private static JcfModel.Model extractModelFrom(FileType fileType, List<String> lines) {
    switch (fileType) {
      case TSV_TRANSACTION_DESCRIPTION_TO_ACCOUNT_NAME_MAPPING:
        return TsvTransactionDescAccountMappingImporter.create(lines).get();
      case LEDGER_ACCOUNT_LISTING:
      case LEDGER_CLI:
        return LedgerFileImporter.create(lines).get();
    }
    // If we reach here, we're not able to extract the model from this type of file.
    // Support must be added first, so go ahead and fail fast.
    LOGGER.atSevere().log(
        "No JcfModel extractor registered to handle file with type: %s", fileType);
    System.exit(1);

    // Should be unreachable.
    Verify.verify(false, "Unreachable.");
    return ModelGenerators.empty();
  }

  private void applySettingsProfiles() {
    List<SettingsProto.SettingsProfiles> allSettingsProfiles =
        FluentIterable.from(settingsProfileFiles)
            .transform(File::new)
            .filter((f) -> f != null && f.exists() && f.isFile())
            .transform(
                (f) -> {
                  try {
                    LOGGER.atInfo().log("Loading settings profiles list from %s", f);
                    SettingsProto.SettingsProfiles.Builder profilesBuilder =
                        SettingsProto.SettingsProfiles.newBuilder();
                    TextFormat.merge(Files.newBufferedReader(f.toPath()), profilesBuilder);
                    return profilesBuilder.build();
                  } catch (IOException e) {
                    LOGGER.atWarning().withCause(e).log(
                        "Could not load settings profile from file %s", f);
                    return SettingsProto.SettingsProfiles.getDefaultInstance();
                  }
                })
            .toList();
    Set<String> ignoredProfiles = new HashSet<>();
    for (SettingsProfiles settingsProfiles : allSettingsProfiles) {
      for (SettingsProfile profile : settingsProfiles.getSettingsProfileList()) {
        if (!profile.getName().isEmpty() && !enabledProfiles.contains(profile.getName())) {
          ignoredProfiles.add(profile.getName());
          continue;
        }
        applyProfile(profile);
      }
    }
    LOGGER.atInfo().log(
        "Ignored the follow settings profiles because they were not enabled by name: %s",
        Joiner.on(", ").join(ignoredProfiles));
  }

  private void applyProfile(SettingsProfile profile) {
    String profileName = profile.getName().isEmpty() ? "global" : profile.getName();
    if (!profile.getCsvFieldPositions().getPositionList().isEmpty()) {
      csvFieldMapping =
          ImmutableMap.copyOf(
              FluentIterable.from(profile.getCsvFieldPositions().getPositionList())
                  .transform((fp) -> Maps.immutableEntry(fp.getField(), fp.getColumnIndex())));
      LOGGER.atInfo().log(
          "From profile '%s', using CSV field positions [%s]",
          profileName, Joiner.on(", ").withKeyValueSeparator(" -> ").join(csvFieldMapping));
    }
    if (!profile.getCsvDateFormatJava().isEmpty()) {
      dateFormat = profile.getCsvDateFormatJava();
      LOGGER.atInfo().log("From profile '%s', using CSV date format %s", profileName, dateFormat);
    }
    if (!profile.getCsvAccountName().isEmpty()) {
      csvAccountName = profile.getCsvAccountName();
      LOGGER.atInfo().log(
          "From profile '%s', assuming CSV import is from account %s", profileName, csvAccountName);
    }
    if (!profile.getImportAccountNamePrefix().isEmpty()) {
      importAccountPrefix = profile.getImportAccountNamePrefix();
      LOGGER.atInfo().log(
          "From profile '%s', assuming import account name prefix %s",
          profileName, importAccountPrefix);
    }
  }

  @Override
  public String getReportType() {
    return reportType;
  }

  @Override
  public Model getInitialModel() {
    return initialModelSupplier.get();
  }

  @Override
  public Optional<File> getDeclaredOutputFile() {
    return outputFile;
  }

  @Override
  public Function<String, Account> getImportAccountGenerator() {
    if (!csvAccountName.equals(UNSET_FLAG)) {
      return (unused) -> ModelGenerators.simpleAccount(csvAccountName);
    }
    if (!importAccountPrefix.equals(UNSET_FLAG)) {
      return (identifier) -> ModelGenerators.simpleAccount(importAccountPrefix.concat(identifier));
    }
    return (unused) -> ModelGenerators.simpleAccount("Imbalance (Account not set)");
  }

  @Override
  public Optional<DateTimeFormatter> getCsvDateFormat() {
    if (getInputCsvLines().isEmpty()) {
      return Optional.empty();
    }
    if (!dateFormat.isEmpty()) {
      return Optional.of(DateTimeFormatter.ofPattern(dateFormat));
    }
    return Optional.ofNullable(
            promptEvaluator.blockingGetResult(
                DateTimeFormatPrompt.usingExamples(
                    FluentIterable.from(getInputCsvLines())
                        .skip(1)
                        .limit(10)
                        .filter(Predicates.notNull())
                        .transform(
                            (line) -> {
                              List<String> fields =
                                  CsvTransactionListingImporter.CSV_SPLITTER.apply(
                                      checkNotNull(line));
                              Integer index = getCsvFieldMappings().get(DataField.DATE);
                              checkNotNull(
                                  index,
                                  "No CSV field mapping declared field DATE. Please provide one.");
                              checkElementIndex(
                                  index,
                                  fields.size(),
                                  "CSV field index for DATE was out of bounds");
                              return fields.get(index);
                            })
                        .filter(Predicates.notNull()))))
        .flatMap(Result::instance);
  }

  @Override
  public ImmutableList<String> getInputCsvLines() {
    return inputCsv.lines();
  }

  @Override
  public ImmutableMap<DataField, Integer> getCsvFieldMappings() {
    return csvFieldMapping;
  }

  @Override
  public boolean needsHelp() {
    return userWantsHelp
        || (!IS_ENVIRONMENT_SATISFACTORY_PREDICATE_BY_COMMAND
            .getOrDefault(
                FluentIterable.from(Command.values())
                    .firstMatch(command -> command.name.equals(mainArgument))
                    .or(Command.UNKNOWN),
                Predicates.alwaysTrue())
            .apply(this));
  }

  @Override
  public void printHelpTextTo(StringBuilder usageStringBuilder) {
    lazyCommandLineParser.get().getUsageFormatter().usage(usageStringBuilder);
  }

  @Override
  public PromptEvaluator getPromptEvaluator() {
    return promptEvaluator;
  }

  public enum Command {
    UNKNOWN(""),
    CSV_MATCHER("csv_matcher"),
    GENERATE_REPORT("generate_report");

    private final String name;

    Command(String name) {
      this.name = name;
    }
  }
}
