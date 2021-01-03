# Bazel file for building and running JCF.

load("@rules_proto//proto:defs.bzl", "proto_library")

java_library(
    name = "jcf_main_lib",
    srcs = glob([
        "src/main/java/net/brentwalther/jcf/**/*.java",
    ]),
    # TODO: Break this up in to sub targets and remove this wide visibility.
    visibility = ["//:__subpackages__"],
    deps = [
        ":autovalue",
        ":jcf_model_java_proto",
        ":jcf_settings_profile_java_proto",
        "@maven//:com_beust_jcommander",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_flogger_flogger_system_backend",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_re2j_re2j",
        "@maven//:com_webcohesion_ofx4j_ofx4j",
        "@maven//:org_jline_jline",
        "@maven//:org_xerial_sqlite_jdbc",
    ],
)

java_binary(
    name = "jcf",
    srcs = [
        "src/main/java/net/brentwalther/jcf/App.java",
    ],
    main_class = "net.brentwalther.jcf.App",
    deps = [
        ":jcf_main_lib",
        ":jcf_model_java_proto",
        "@maven//:com_beust_jcommander",
        # "@maven//:junit_junit"
        # "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_binary(
    name = "csv_matcher",
    srcs = [
        "src/main/java/net/brentwalther/jcf/CsvMatcher.java",
    ],
    main_class = "net.brentwalther.jcf.CsvMatcher",
    deps = [
        ":jcf_main_lib",
        ":jcf_model_java_proto",
        ":jcf_settings_profile_java_proto",
        "@maven//:com_beust_jcommander",
        "@maven//:com_google_flogger_flogger",
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
