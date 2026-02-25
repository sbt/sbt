scalaVersion := "3.6.4"
run / fork := true
// Force arguments file mode by exceeding MaxConcatenatedOptionLength (5000)
run / javaOptions += ("-Dsome.long.property=" + ("X" * 5000))
// Pass JSON with double quotes as a system property — this goes through the argsfile
run / javaOptions += """-Djson={"a":1}"""
