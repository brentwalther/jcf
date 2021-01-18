package(default_visibility = ["//visibility:public"])

java_library(
    name = "model",
    srcs = [
        "FileType.java",
        "IndexedModel.java",
        "ModelGenerators.java",
        "ModelTransforms.java",
        "ModelValidations.java",
    ],
    deps = [
        "//:jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/string:formatter",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
    ],
)
