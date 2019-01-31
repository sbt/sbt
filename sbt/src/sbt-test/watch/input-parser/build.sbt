import sbt.input.parser.Build

watchInputStream := Build.inputStream

watchStartMessage := { count =>
  Build.outputStream.write('\n'.toByte)
  Build.outputStream.flush()
  Some("default start message")
}