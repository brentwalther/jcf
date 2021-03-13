java_test(
    name = "csv_transaction_listing_importer_test",
    srcs = [
        "CsvTransactionListingImporterTest.java",
    ],
    test_class = "net.brentwalther.jcf.model.importer.CsvTransactionListingImporterTest",
    deps = [
        "//:jcf_model_java_proto",
        "//:jcf_settings_profile_java_proto",
        "//src/main/java/net/brentwalther/jcf/environment",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/model/importer",
        "//src/test/java/net/brentwalther/jcf/testing:correspondences",
        "@maven//:com_google_flogger_flogger_system_backend",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_all",
    ],
)

java_test(
    name = "ledger_importer_test",
    srcs = [
        "LedgerFileImporterTest.java",
    ],
    test_class = "net.brentwalther.jcf.model.importer.LedgerFileImporterTest",
    deps = [
        "//:jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/model/importer",
        "//src/test/java/net/brentwalther/jcf/testing:correspondences",
        "@maven//:com_google_flogger_flogger_system_backend",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)
