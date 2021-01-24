package(default_visibility = ["//visibility:public"])

java_library(
    name = "impl",
    srcs = [
        "ErrorUi.java",
        "SwingUiImpl.java",
    ],
    deps = [
        "//:jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/environment",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/string:formatter",
        "//src/main/java/net/brentwalther/jcf/ui/swing",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
    ],
)
