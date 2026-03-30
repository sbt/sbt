@transient
lazy val checkMetadata = taskKey[Unit]("check maven-metadata-local.xml is written for SNAPSHOT")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.13.16",
    organization := "com.example.test.scripted",
    version := "0.1.0-SNAPSHOT",
    name := "publishm2-metadata-test",
    checkMetadata := {
      val m2Dir = new File(
        System.getProperty("user.home"),
        ".m2/repository/com/example/test/scripted/publishm2-metadata-test_2.13/0.1.0-SNAPSHOT"
      )
      val metadataFile = new File(m2Dir, "maven-metadata-local.xml")
      assert(metadataFile.exists, s"maven-metadata-local.xml not found at $metadataFile")
      val content = IO.read(metadataFile)
      assert(content.contains("<localCopy>true</localCopy>"), s"Missing localCopy element in:\n$content")
      assert(content.contains("<groupId>com.example.test.scripted</groupId>"), s"Wrong groupId in:\n$content")
    },
  )
