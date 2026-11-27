package sbt.internal

import hedgehog.*
import hedgehog.runner.*
import xsbti.VirtualFileRef

object CompilerPipeliningOptionsTest extends Properties:
  private val jar = VirtualFileRef.of("${OUT}/early/main.jar")
  private val options = Seq("-deprecation", "-Ypickle-write", "user.jar", "-feature")
  private val pipelined = Seq("-Ypickle-java", "-Ypickle-write", jar.id) ++ options

  override def tests: List[Test] = List(
    example(
      "disabled pipelining preserves user options",
      Result.assert(Compiler.compileScalacOptions(options, "3.5.0", None) == options)
    ),
    example(
      "Scala 3 before 3.5 keeps its existing options",
      Result.assert(Compiler.compileScalacOptions(options, "3.4.3", Some(jar)) == options)
    ),
    example(
      "Scala 3.5 prepends the virtual early output",
      Result.assert(Compiler.compileScalacOptions(options, "3.5.0", Some(jar)) == pipelined)
    ),
    example(
      "Scala 2 prepends the virtual early output",
      Result.assert(Compiler.compileScalacOptions(options, "2.13.16", Some(jar)) == pipelined)
    )
  )
end CompilerPipeliningOptionsTest
