package sbt.internal

import scala.reflect.ClassTag
import sjsonnew.*
import xsbti.HashedVirtualFileRef

// CompileOption has the list of sources etc
case class CompileInputs2(
    classpath: Seq[HashedVirtualFileRef],
    sources: Seq[HashedVirtualFileRef],
)

object CompileInputs2:
  import sbt.util.CacheImplicits.given

  given IsoLList.Aux[
    CompileInputs2,
    Vector[HashedVirtualFileRef] :*: Vector[HashedVirtualFileRef] :*: LNil
  ] =
    LList.iso(
      { (v: CompileInputs2) =>
        ("classpath", v.classpath.toVector) :*: ("sources", v.sources.toVector) :*: LNil
      },
      { (in: Vector[HashedVirtualFileRef] :*: Vector[HashedVirtualFileRef] :*: LNil) =>
        CompileInputs2(in.head, in.tail.head)
      }
    )
  given JsonFormat[CompileInputs2] = summon
end CompileInputs2
