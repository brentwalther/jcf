package(default_visibility = ["//visibility:public"])

java_library(
    name = "screen",
    srcs = [
        "MainMenuScreen.java",
        "ModelReviewScreen.java",
        "OFXImportScreen.java",
        "Screen.java",
        "SplitMatcherScreen.java",
    ],
    deps = [
        "//:autovalue",
        "//:jcf_model_java_proto",
        "//:jcf_settings_profile_java_proto",
        "//src/main/java/net/brentwalther/jcf/export",
        "//src/main/java/net/brentwalther/jcf/matcher",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/model/importer",
        "//src/main/java/net/brentwalther/jcf/prompt",
        "//src/main/java/net/brentwalther/jcf/prompt:api",
        "//src/main/java/net/brentwalther/jcf/string:formatter",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
    ],
)
