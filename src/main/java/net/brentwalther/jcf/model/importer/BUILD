package(default_visibility = ["//visibility:public"])

java_library(
    name = "importer",
    srcs = [
        "CsvTransactionListingImporter.java",
        "JcfModelImporter.java",
        "LedgerFileImporter.java",
        "OfxConnector.java",
        "SQLiteConnector.java",
        "SQLiteResultSetMatcher.java",
        "TsvTransactionDescAccountMappingImporter.java",
    ],
    deps = [
        "//:jcf_model_java_proto",
        "//:jcf_settings_profile_java_proto",
        "//src/main/java/net/brentwalther/jcf/environment",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/prompt:api",
        "//src/main/java/net/brentwalther/jcf/string:formatter",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_re2j_re2j",
        "@maven//:com_webcohesion_ofx4j_ofx4j",
    ],
)
