# Bazel file for building and running JCF.

load("@rules_proto//proto:defs.bzl", "proto_library")

java_binary(
    name = "jcf",
    srcs = glob([
        "src/main/java/net/brentwalther/jcf/**/*.java"
    ]),
    main_class = "net.brentwalther.jcf.App",
    deps = [
        ":jcf_model_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_webcohesion_ofx4j_ofx4j",
        "@maven//:commons_cli_commons_cli",
        "@maven//:org_jline_jline",
        "@maven//:org_xerial_sqlite_jdbc",
        # "@maven//:junit_junit"
        # "@maven//:org_hamcrest_hamcrest_library",
    ]
)

java_proto_library(
    name = "jcf_model_java_proto",
    deps = [":jcf_model_proto"],
)

proto_library(
    name = "jcf_model_proto",
    srcs = [
        "src/main/proto/model.proto"
    ],
)