import sbt.internal.LogManager
import sbt.internal.util.{ Appender, ConsoleAppender, ConsoleOut }
import java.io.{ FileWriter, PrintWriter }

lazy val checkBgOutput = taskKey[Unit]("Verify the bgRun forked process output was logged")
lazy val waitForAllBgJobs = taskKey[Unit]("Wait for every bgJobService job to terminate")
lazy val outFile = settingKey[File]("File where bgRun output is captured for the test")

lazy val root = project
  .in(file("."))
  .settings(
    run / fork := true,
    outFile := baseDirectory.value / "target" / "bg-output.log",
    // Override logManager so the background logger's relay appender writes
    // to a file. This lets the test assert that forked-process output
    // reached the managed logger (rather than going to the JVM's stdout via
    // inheritIO, which is what happens when the bgRun fork-output bug is
    // present). In a scripted test there are no network channels, so the
    // default relay appender has no observable effect anyway.
    logManager := {
      val ea = extraAppenders.value
      val f = outFile.value
      IO.touch(f)
      val fileRelay: Unit => Appender = _ => {
        val pw = new PrintWriter(new FileWriter(f, true), true)
        ConsoleAppender("bg-file-test", ConsoleOut.printWriterOut(pw))
      }
      LogManager.withLoggers(
        screen = (task, state) => ConsoleAppender(s"screen-${task.key.label}"),
        relay = fileRelay,
        extra = ea
      )
    },
    waitForAllBgJobs := Def.uncached {
      val service = bgJobService.value
      service.jobs.foreach(service.waitFor)
    },
    checkBgOutput := Def.uncached {
      val f = outFile.value
      val content = IO.read(f)
      assert(content.contains("foobar"), s"Expected 'foobar' in $f, got:\n$content")
    }
  )
