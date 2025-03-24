package sbt.internal

import scala.reflect.ClassTag
import sjsonnew.*
import xsbti.{ HashedVirtualFileRef, VirtualFileRef }

// CompileOption has the list of sources etc
case class CompileInputs2(
    classpath: Vector[HashedVirtualFileRef],
    sources: Vector[HashedVirtualFileRef],
    scalacOptions: Vector[String],
    javacOptions: Vector[String],
    outputPath: VirtualFileRef,
    cachePath: VirtualFileRef
)

object CompileInputs2:
  import sbt.util.CacheImplicits.given

  given IsoLList.Aux[
    CompileInputs2,
    Vector[HashedVirtualFileRef] :*: Vector[HashedVirtualFileRef] :*: Vector[String] :*:
      Vector[String] :*: VirtualFileRef :*: VirtualFileRef :*: LNil
  ] =
    LList.iso(
      { (v: CompileInputs2) =>
        ("classpath", v.classpath) :*:
          ("sources", v.sources) :*:
          ("scalacOptions", v.scalacOptions) :*:
          ("javacOptions", v.javacOptions) :*:
          ("outputPath", v.outputPath) :*:
          ("cachePath", v.cachePath) :*:
          LNil
      },
      {
        (in: Vector[HashedVirtualFileRef] :*: Vector[HashedVirtualFileRef] :*: Vector[String] :*:
          Vector[String] :*: VirtualFileRef :*: VirtualFileRef :*: LNil) =>
          CompileInputs2(
            in.head,
            in.tail.head,
            in.tail.tail.head,
            in.tail.tail.tail.head,
            in.tail.tail.tail.tail.head,
            in.tail.tail.tail.tail.tail.head
          )
      }
    )
end CompileInputs2
