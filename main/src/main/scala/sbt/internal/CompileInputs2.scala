package sbt.internal

import scala.reflect.ClassTag
import sjsonnew.*
import xsbti.HashedVirtualFileRef

// CompileOption has the list of sources etc
case class CompileInputs2(
    classpath: Vector[HashedVirtualFileRef],
    sources: Vector[HashedVirtualFileRef],
    scalacOptions: Vector[String],
    javacOptions: Vector[String],
)

object CompileInputs2:
  import sbt.util.CacheImplicits.given

  given IsoLList.Aux[
    CompileInputs2,
    Vector[HashedVirtualFileRef] :*: Vector[HashedVirtualFileRef] :*: Vector[String] :*:
      Vector[String] :*: LNil
  ] =
    LList.iso(
      { (v: CompileInputs2) =>
        ("classpath", v.classpath) :*:
          ("sources", v.sources) :*:
          ("scalacOptions", v.scalacOptions) :*:
          ("javacOptions", v.javacOptions) :*:
          LNil
      },
      {
        (in: Vector[HashedVirtualFileRef] :*: Vector[HashedVirtualFileRef] :*: Vector[String] :*:
          Vector[String] :*: LNil) =>
          CompileInputs2(in.head, in.tail.head, in.tail.tail.head, in.tail.tail.tail.head)
      }
    )
end CompileInputs2
