scala_library(
    name = "core",
    dependencies = [
        "3rdparty/jvm:fastParse",
        "3rdparty/jvm:jsoup",
        # TODO(wisechengyi) for some reason there is no compile error
        # and this is needed at runtime.
        "3rdparty/jvm:scala-xml",
    ],
    sources = rglobs("jvm/*.scala", "shared/*.scala"),
)
