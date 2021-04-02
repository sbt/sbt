import java.nio.file.{ Files, Paths }

watchBeforeCommand := { () => Files.write(Paths.get("foo"), "foo".getBytes) }

watchOnIteration := { (_, _, _) => sbt.nio.Watch.CancelWatch }
