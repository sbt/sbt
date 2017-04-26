import sbt.contraband.ast._
import sbt.contraband.CodecCodeGen

object DatatypeConfig {

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
    case "scala.xml.NodeSeq" => { _ =>
      "sbt.internal.librarymanagement.formats.NodeSeqFormat" :: Nil
    }

    case "org.apache.ivy.plugins.resolver.DependencyResolver" => { _ =>
      "sbt.internal.librarymanagement.formats.DependencyResolverFormat" :: Nil
    }

    case "xsbti.GlobalLock" => { _ =>
      "sbt.internal.librarymanagement.formats.GlobalLockFormat" :: Nil
    }
    case "xsbti.Logger" => { _ =>
      "sbt.internal.librarymanagement.formats.LoggerFormat" :: Nil
    }

    case "sbt.librarymanagement.UpdateOptions" => { _ =>
      "sbt.internal.librarymanagement.formats.UpdateOptionsFormat" :: Nil
    }

    // TODO: These are handled by BasicJsonProtocol, and sbt-datatype should handle them by default, imo
    case "Option" | "Set" | "scala.Vector" => { tpe =>
      getFormats(oneArg(tpe))
    }
    case "Map" | "Tuple2" | "scala.Tuple2" => { tpe =>
      twoArgs(tpe).flatMap(getFormats)
    }
    case "Int" | "Long" => { _ =>
      Nil
    }
  }

  /** Types for which we don't include the format -- they're just aliases to InclExclRule */
  val excluded = Set("sbt.librarymanagement.InclusionRule", "sbt.librarymanagement.ExclusionRule")

  /** Returns the list of formats required to encode the given `TpeRef`. */
  val getFormats: Type => List[String] =
    CodecCodeGen.extensibleFormatsForType {
      case NamedType(List("sbt", "internal", "librarymanagement", "RetrieveConfiguration"), _) =>
        "sbt.librarymanagement.RetrieveConfigurationFormats" :: Nil
      case tpe: Type if myCodecs isDefinedAt tpe.removeTypeParameters.name =>
        myCodecs(tpe.removeTypeParameters.name)(tpe)
      case tpe: Type if excluded contains tpe.removeTypeParameters.name =>
        Nil
      case other =>
        CodecCodeGen.formatsForType(other)
    }

}
