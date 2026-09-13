import scala.sys.process.*

lazy val check = taskKey[Unit]("Verifies the repository override property applies to an sbt 1 meta-build")

lazy val root = (project in file(".")).settings(
  check := {
    val base = baseDirectory.value
    val globalBase = base / "global"
    val launcher = file(sys.props("sbt.launch.jar"))
    val java = file(sys.props("java.home")) / "bin" / "java"
    val exitCode = Process(
      Seq(
        java.getAbsolutePath,
        "-Dsbt.override.build.repos=true",
        s"-Dsbt.repository.config=${(base / "repositories").getAbsolutePath}",
        s"-Dsbt.global.base=${globalBase.getAbsolutePath}",
        s"-Dsbt.boot.directory=${(globalBase / "boot").getAbsolutePath}",
        "-Dsbt.server.autostart=false",
        "-jar",
        launcher.getAbsolutePath,
        "reload plugins",
        "checkMetaBuildResolvers"
      ),
      base / "nested"
    ).!
    assert(exitCode == 0, s"nested sbt exited with status $exitCode")
  }
)
