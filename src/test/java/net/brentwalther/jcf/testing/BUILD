java_library(
    name = "correspondences",
    testonly = 1,
    srcs = [
        "Correspondences.java",
    ],
    visibility = ["//src/test/java/net/brentwalther/jcf:__subpackages__"],
    deps = [
        "//:jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/model",
        "@maven//:com_google_truth_truth",
    ],
)

java_library(
    name = "argument_matchers",
    testonly = 1,
    srcs = [
        "ArgumentMatchers.java",
    ],
    visibility = ["//src/test/java/net/brentwalther/jcf:__subpackages__"],
    deps = [
        "//src/main/java/net/brentwalther/jcf/prompt",
        "//src/main/java/net/brentwalther/jcf/prompt:api",
        "@maven//:org_hamcrest_hamcrest_library",
        "@maven//:org_mockito_mockito_all",
    ],
)
