Global/additionalJavaHomes += "6" -> file("/good/old/times/java-6")

TaskKey[Unit]("check") := {
  assert(discoveredJavaHomes.value("6").getAbsolutePath.contains("java-6"))
}
