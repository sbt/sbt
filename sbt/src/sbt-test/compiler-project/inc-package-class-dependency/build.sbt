import sbt.internal.inc.Analysis

TaskKey[Unit]("verify-binary-deps") <<= (compile in Compile, classDirectory in Compile, baseDirectory) map {
  case (a: Analysis, classDir: java.io.File, base: java.io.File) =>
    val nestedPkgClass = classDir / "test/nested.class"
    val fooSrc = base / "src/main/scala/test/nested/Foo.scala"
    assert(!a.relations.binaryDeps(fooSrc).contains(nestedPkgClass), a.relations.toString)
}
