import java.nio.file.Files

lazy val watchLoopTest = taskKey[Unit]("Check that managed sources are filtered")

sourceGenerators in Compile += Def.task {
  val path = baseDirectory.value.toPath.resolve("src/main/scala/Foo.scala")
  Files.write(path, "object Foo".getBytes).toFile :: Nil
}

watchLoopTest := {
  val watched = watchSources.value
  val managedSource = (managedSources in Compile).value.head
  assert(!SourceWrapper.accept(watched, managedSource))
  assert((sources in Compile).value.foldLeft((true, Set.empty[File])) {
    case ((res, set), f) => (res && !set.contains(f), set + f)
  }._1)
}
