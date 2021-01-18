package(default_visibility = ["//visibility:public"])

java_library(
    name = "flag",
    srcs = [
        "CsvSetFlag.java",
        "DataFieldExtractor.java",
        "JcfEnvironmentFlagFactory.java",
        "NonExistentFile.java",
        "TextFileToLinesConverter.java",
    ],
    deps = [
        "//:jcf_settings_profile_java_proto",
        "@maven//:com_beust_jcommander",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
    ],
)
