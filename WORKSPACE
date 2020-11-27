load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Download and apply the proto rules.
http_archive(
    name = "rules_proto",
    sha256 = "602e7161d9195e50246177e7c55b2f39950a9cf7366f74ed5f22fd45750cd208",
    strip_prefix = "rules_proto-97d8af4dc474595af3900dd85cb3a29ad28cc313",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_proto/archive/97d8af4dc474595af3900dd85cb3a29ad28cc313.tar.gz",
        "https://github.com/bazelbuild/rules_proto/archive/97d8af4dc474595af3900dd85cb3a29ad28cc313.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

# Download and apply the maven rules with all the artifacts we want.
RULES_JVM_EXTERNAL_TAG = "3.0"

RULES_JVM_EXTERNAL_SHA = "62133c125bf4109dfd9d2af64830208356ce4ef8b165a6ef15bbff7460b35c3a"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "com.beust:jcommander:1.78",
        "com.google.flogger:flogger:0.5.1",
        "com.google.flogger:flogger-system-backend:0.5.1",
        "com.google.guava:guava:28.2-jre",
        "com.google.protobuf:protobuf-java:3.0.0",
        "com.google.re2j:re2j:1.5",
        "com.google.truth:truth:1.1",
        "com.webcohesion.ofx4j:ofx4j:1.7",
        "org.jline:jline:3.1.3",
        "org.xerial:sqlite-jdbc:3.7.2",
        "junit:junit:4.12",
        # "org.hamcrest:hamcrest-library:1.3",
    ],
    repositories = [
        # "https://jcenter.bintray.com/",
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)
