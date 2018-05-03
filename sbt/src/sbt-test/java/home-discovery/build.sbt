Global / javaHomes += JavaVersion("6") -> file("/good/old/times/java-6")

TaskKey[Unit]("check") := {
  assert(fullJavaHomes.value(JavaVersion("1.6")).getAbsolutePath.contains("java-6"))
}
