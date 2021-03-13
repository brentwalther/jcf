package(default_visibility = ["//visibility:public"])

java_library(
    name = "prompt",
    srcs = [
        "AccountPickerPrompt.java",
        "BigDecimalPrompt.java",
        "DateTimeFormatPrompt.java",
        "FilePrompt.java",
        "ModelPickerPrompt.java",
        "NoticePrompt.java",
        "OptionsPrompt.java",
        "PromptBuilder.java",
        "PromptDecorator.java",
    ],
    deps = [
        ":api",
        "//:autovalue",
        "//:jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/model",
        "//src/main/java/net/brentwalther/jcf/string:formatter",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "api",
    srcs = [
        "Prompt.java",
        "PromptEvaluator.java",
        "SizeBounds.java",
    ],
    deps = [
        "//:autovalue",
        "//:jcf_model_java_proto",
        "//src/main/java/net/brentwalther/jcf/model",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "terminal_printing_prompt_evaluator",
    srcs = [
        "SpecialCharacters.java",
        "impl/TerminalPromptEvaluator.java",
    ],
    deps = [
        ":api",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:org_jline_jline",
    ],
)
