# Bazel file for building and running JCF.
load("@rules_proto//proto:defs.bzl", "proto_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "jcf_environment_impl",
    srcs = [
        "src/main/java/net/brentwalther/jcf/JcfEnvironmentImpl.java",
    ],
    deps = [
        ":jcf_model_java_proto",
        ":jcf_settings_profile_java_proto",
        "//src/main/java/net/brentwalther/jcf/environment",
        "//src/main/java/net/brentwalther/jcf/flag",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/model/importer",
        "//src/main/java/net/brentwalther/jcf/prompt",
        "//src/main/java/net/brentwalther/jcf/prompt:api",
        "//src/main/java/net/brentwalther/jcf/prompt:terminal_printing_prompt_evaluator",
        "@maven//:com_beust_jcommander",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
    ],
)

java_binary(
    name = "jcf",
    srcs = [
        "src/main/java/net/brentwalther/jcf/App.java",
    ],
    main_class = "net.brentwalther.jcf.App",
    deps = [
        ":jcf_environment_impl",
        ":jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/model/importer",
        "//src/main/java/net/brentwalther/jcf/prompt",
        "//src/main/java/net/brentwalther/jcf/prompt:terminal_printing_prompt_evaluator",
        "//src/main/java/net/brentwalther/jcf/screen",
        "@maven//:com_beust_jcommander",
        "@maven//:com_google_flogger_flogger_system_backend",
        "@maven//:org_jline_jline",
    ],
)

java_binary(
    name = "csv_matcher",
    srcs = [
        "src/main/java/net/brentwalther/jcf/CsvMatcher.java",
    ],
    main_class = "net.brentwalther.jcf.CsvMatcher",
    deps = [
        ":jcf_environment_impl",
        ":jcf_model_java_proto",
        ":jcf_settings_profile_java_proto",
        "//src/main/java/net/brentwalther/jcf/environment",
        "//src/main/java/net/brentwalther/jcf/export",
        "//src/main/java/net/brentwalther/jcf/matcher",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/model/importer",
        "//src/main/java/net/brentwalther/jcf/screen",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_flogger_flogger_system_backend",
        "@maven//:com_google_guava_guava",
    ],
)

java_proto_library(
    name = "jcf_model_java_proto",
    visibility = ["//:__subpackages__"],
    deps = [":jcf_model_proto"],
)

proto_library(
    name = "jcf_model_proto",
    srcs = [
        "src/main/proto/model.proto",
    ],
)

java_proto_library(
    name = "jcf_settings_profile_java_proto",
    visibility = ["//:__subpackages__"],
    deps = [":jcf_settings_profile_proto"],
)

proto_library(
    name = "jcf_settings_profile_proto",
    srcs = [
        "src/main/proto/settings_profile.proto",
    ],
)

java_plugin(
    name = "autovalue_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@maven//:com_google_auto_value_auto_value",
    ],
)

java_library(
    name = "autovalue",
    exported_plugins = [
        ":autovalue_plugin",
    ],
    neverlink = 1,
    exports = [
        "@maven//:com_google_auto_value_auto_value",
    ],
)
