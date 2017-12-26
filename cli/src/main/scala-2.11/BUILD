scala_library(
    name = "cli",
    dependencies = [
        "3rdparty/jvm:argonaut-shapeless",
        "3rdparty/jvm:caseapp",
        "cache/src/main/scala:cache",
        "core:core",
        "extra/src/main/scala/coursier/extra:extra",
        "extra/src/main/scala-2.11/coursier/extra:native",
    ],
    sources = rglobs("*.scala"),
)

jvm_binary(
    name = "coursier-cli",
    basename = "coursier-cli",
    dependencies = [
        ":cli",
    ],
    main = "coursier.cli.Coursier",
)
