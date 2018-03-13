junit_tests(
    name = "test",
    dependencies = [
        ":lib",
        "3rdparty/jvm:scalatest",
        "cli/src/main/scala-2.12:cli",
    ],
    sources = rglobs("*.scala", exclude = ["CliTestLib.scala", "CliBootstrapIntegrationTest.scala"]),
)

scala_library(
    name='lib',
    dependencies = [
        "cache:cache"
    ],
    sources = ["CliTestLib.scala"],
)
