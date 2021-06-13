package(default_visibility = ["//visibility:public"])

java_library(
    name = "report",
    srcs = [
        "TsvExpensesByMonthReport.java",
        "TsvNetworthReport.java",
    ],
    deps = [
        "//:jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/string:formatter",
        "@maven//:com_google_guava_guava",
    ],
)
