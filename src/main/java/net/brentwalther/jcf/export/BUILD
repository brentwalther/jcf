package(default_visibility = ["//visibility:public"])

java_library(
    name = "export",
    srcs = [
        "CsvExporter.java",
        "JcfModelExporter.java",
        "LedgerExporter.java",
    ],
    deps = [
        "//:autovalue",
        "//:jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/string:formatter",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
    ],
)
