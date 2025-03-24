import sbt.contraband.ast.*
import sbt.contraband.CodecCodeGen

object ContrabandConfig {

  /** Extract the only type parameter from a TpeRef */
  def oneArg(tpe: Type): Type = {
    val pat = s"""${tpe.removeTypeParameters.name}[<\\[](.+?)[>\\]]""".r
    val pat(arg0) = tpe.name
    NamedType(arg0 split '.' toList)
  }

  /** Extract the two type parameters from a TpeRef */
  def twoArgs(tpe: Type): List[Type] = {
    val pat = s"""${tpe.removeTypeParameters.name}[<\\[](.+?), (.+?)[>\\]]""".r
    val pat(arg0, arg1) = tpe.name
    NamedType(arg0 split '.' toList) :: NamedType(arg1 split '.' toList) :: Nil
  }

  /** Codecs that were manually written. */
  val myCodecs: PartialFunction[String, Type => List[String]] = {
    // TODO: These are handled by BasicJsonProtocol, and sbt-contraband should handle them by default, imo
    case "Option" | "Set" | "scala.Vector" => { tpe =>
      getFormats(oneArg(tpe))
    }
    case "Map" | "Tuple2" | "scala.Tuple2" => { tpe =>
      twoArgs(tpe).flatMap(getFormats)
    }
    case "Int" | "Long" => { _ =>
      Nil
    }
    case "sbt.testing.Status" => { _ =>
      "sbt.internal.testing.StatusFormats" :: Nil
    }
    case "scalajson.ast.unsafe.JValue" | "sjsonnew.shaded.scalajson.ast.unsafe.JValue" => { _ =>
      "sbt.internal.util.codec.JValueFormats" :: Nil
    }
    case "xsbti.HashedVirtualFileRef" => { _ =>
      "sbt.internal.util.codec.HashedVirtualFileRefFormats" :: Nil
    }
    case "java.nio.ByteBuffer" => { _ =>
      "sbt.internal.util.codec.ByteBufferFormats" :: Nil
    }
  }

  /** Returns the list of formats required to encode the given `TpeRef`. */
  val getFormats: Type => List[String] =
    CodecCodeGen.extensibleFormatsForType {
      case tpe: Type if myCodecs isDefinedAt tpe.removeTypeParameters.name =>
        myCodecs(tpe.removeTypeParameters.name)(tpe)
      case other => CodecCodeGen.formatsForType(other)
    }
}
