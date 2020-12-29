package net.brentwalther.jcf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
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
import net.brentwalther.jcf.SettingsProto.SettingsProfile;
import net.brentwalther.jcf.SettingsProto.SettingsProfile.DataField;
import net.brentwalther.jcf.SettingsProto.SettingsProfiles;
import net.brentwalther.jcf.flag.CsvSetFlag;
import net.brentwalther.jcf.flag.DataFieldExtractor;
import net.brentwalther.jcf.flag.JcfEnvironmentFlagFactory;
import net.brentwalther.jcf.flag.NonExistentFile;
import net.brentwalther.jcf.flag.TextFileToLinesConverter.EagerlyLoadedTextFile;
import net.brentwalther.jcf.model.FileType;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.ModelGenerator;
import net.brentwalther.jcf.model.importer.LedgerFileImporter;
import net.brentwalther.jcf.model.importer.TsvTransactionDescAccountMappingImporter;
import net.brentwalther.jcf.prompt.AccountPickerPrompt;
import net.brentwalther.jcf.prompt.PromptDecorator;
import net.brentwalther.jcf.prompt.PromptEvaluator;
import net.brentwalther.jcf.screen.DateTimeFormatChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class JcfEnvironment {

  /** The empty string, representing an unset string flag value. */
  private static final String UNSET = "";

  private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
  private final Supplier<JCommander> commandLineParserSupplier =
      Suppliers.memoize(
          () ->
              JCommander.newBuilder()
                  .addObject(this)
                  .addConverterFactory(JcfEnvironmentFlagFactory.INSTANCE)
                  .build());

  @Parameter(
      names = {"--ledger_account_listing"},
      description = "Optional. The file path to a ledger CLI format account listing file.")
  private EagerlyLoadedTextFile ledgerAccountListing = EagerlyLoadedTextFile.EMPTY;

  @Parameter(
      names = {"--master_ledger"},
      description = "Optional. The file path to a ledger CLI format master ledger file.")
  private EagerlyLoadedTextFile masterLedger = EagerlyLoadedTextFile.EMPTY;

  @Parameter(
      names = {"--transaction_csv"},
      description =
          "Required. The file path to a CSV format file which is a list of transactions from a single account, column names included.",
      required = true)
  private EagerlyLoadedTextFile inputCsv = EagerlyLoadedTextFile.EMPTY;

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

  private final Supplier<Model> initialModelSupplier =
      Suppliers.memoize(
          () -> {
            Model model = ModelGenerator.empty();
            // This is a custom format that I added early on instead of just writing a ledger CLI
            // format importer. It's probably just useless cruft now but may as well keep it since
            // it's not broken as far as I am aware.
            if (!descToAccountTsv.lines().isEmpty()) {
              model =
                  ModelGenerator.merge(
                          extractModelFrom(
                              FileType.TSV_TRANSACTION_DESCRIPTION_TO_ACCOUNT_NAME_MAPPING,
                              descToAccountTsv.lines()))
                      .into(model);
            }
            if (!ledgerAccountListing.lines().isEmpty()) {
              JcfModel.Model allAccounts =
                  extractModelFrom(FileType.LEDGER_ACCOUNT_LISTING, ledgerAccountListing.lines());
              model = ModelGenerator.merge(allAccounts).into(model);
            }
            if (!masterLedger.lines().isEmpty()) {
              model =
                  ModelGenerator.merge(extractModelFrom(FileType.LEDGER_CLI, masterLedger.lines()))
                      .into(model);
            }
            LOGGER.atInfo().log(
                "Generated the initial model containing %s accounts and %s transactions.",
                model.getAccountCount(), model.getTransactionCount());
            return model;
          });

  @Parameter(
      names = {"--output"},
      description = "Required. Path to a file to output to. It should not already exist.",
      required = true,
      converter = NonExistentFile.class)
  private Optional<File> outputFile = Optional.empty();

  @Parameter(
      names = {"--help", "-h"},
      description = "Print this help text.",
      help = true)
  private boolean help = false;

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
  private String dateFormat = UNSET;

  private final Supplier<Optional<DateTimeFormatter>> csvDateFormatSupplier =
      Suppliers.memoize(
          () -> {
            if (!dateFormat.isEmpty()) {
              return Optional.of(DateTimeFormatter.ofPattern(dateFormat));
            }
            final ImmutableMap<DataField, Integer> csvFieldMappings = getCsvFieldMappings();
            final Splitter csvSplitter = Splitter.on(',').trimResults().omitEmptyStrings();
            if (getInputCsvLines().isEmpty()) {
              return Optional.empty();
            }
            return Optional.ofNullable(
                DateTimeFormatChooser.obtainFormatForExamples(
                    FluentIterable.from(getInputCsvLines())
                        .skip(1)
                        .limit(10)
                        .filter(Predicates.notNull())
                        .transform(
                            (line) -> {
                              List<String> fields = csvSplitter.splitToList(checkNotNull(line));
                              Integer index = csvFieldMappings.get(DataField.DATE);
                              checkNotNull(
                                  index,
                                  "No CSV field mapping declared field DATE. Please provide one.");
                              Preconditions.checkState(
                                  index >= 0 && index < fields.size(),
                                  "CSV field index for DATE out of bounds " + index);
                              return fields.get(index);
                            })
                        .filter(Predicates.notNull())));
          });

  @Parameter(
      names = {"--csv_account_name"},
      description =
          "Optional. The fully qualified name of the account for which the transactions in the CSV file "
              + "(--transaction_csv) are coming from.")
  private String csvAccountName = UNSET;

  private final Supplier<Account> csvAccountSupplier =
      Suppliers.memoize(
          () ->
              csvAccountName.equals(UNSET)
                  ? PromptEvaluator.showAndGetResult(
                      net.brentwalther.jcf.TerminalProvider.get(),
                      PromptDecorator.decorateWithStatusBars(
                          AccountPickerPrompt.create(initialModel().getAccountList()),
                          ImmutableList.of("Please choose the account this CSV file represents.")))
                  : dummyAccount(csvAccountName));

  public static JcfEnvironment createFromArgs(String[] args) {
    JcfEnvironment context = new JcfEnvironment();
    // Will initialize local @Parameter flags.
    context.commandLineParserSupplier.get().parse(args);
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
    return ModelGenerator.empty();
  }

  private static Account dummyAccount(String accountName) {
    return Account.newBuilder()
        .setId(accountName)
        .setName(accountName)
        .setType(Account.Type.UNKNOWN_TYPE)
        .build();
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
    for (SettingsProfiles settingsProfiles : allSettingsProfiles) {
      for (SettingsProfile profile : settingsProfiles.getSettingsProfileList()) {
        if (!profile.getName().isEmpty() && !enabledProfiles.contains(profile.getName())) {
          LOGGER.atInfo().log(
              "Not applying settings profile because it is not enabled: %s", profile.getName());
          continue;
        }
        applyProfile(profile);
      }
    }
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
  }

  public Model initialModel() {
    return initialModelSupplier.get();
  }

  /** Returns the output file that the user declared. */
  public Optional<File> getDeclaredOutputFile() {
    return outputFile;
  }

  /**
   * Returns the account where the transactions in the input CSV file are occurring. Will be empty
   * if the input CSV file is unspecified/empty.
   */
  public Optional<Account> getCsvAccount() {
    if (getInputCsvLines().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(csvAccountSupplier.get());
  }

  /**
   * Returns the date format for the transactions in the input CSV file. Will be empty if the input
   * CSV file is unspecified/empty.
   */
  public Optional<DateTimeFormatter> getCsvDateFormat() {
    if (getInputCsvLines().isEmpty()) {
      return Optional.empty();
    }
    return csvDateFormatSupplier.get();
  }

  public ImmutableList<String> getInputCsvLines() {
    return inputCsv.lines();
  }

  public ImmutableMap<DataField, Integer> getCsvFieldMappings() {
    return csvFieldMapping;
  }

  public boolean needsHelp() {
    return help;
  }

  public void printHelpTextTo(StringBuilder usageStringBuilder) {
    commandLineParserSupplier.get().getUsageFormatter().usage(usageStringBuilder);
  }
}
