import sbt.contraband.ast.*
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

    case "sbt.librarymanagement.ivy.UpdateOptions" => { _ =>
      "sbt.librarymanagement.ivy.formats.UpdateOptionsFormat" :: Nil
    }

    case "sbt.librarymanagement.LogicalClock" => { _ =>
      "sbt.internal.librarymanagement.formats.LogicalClockFormats" :: Nil
    }

    case "sbt.librarymanagement.CrossVersion" => { _ =>
      "sbt.librarymanagement.CrossVersionFormats" ::
        "sbt.librarymanagement.DisabledFormats" ::
        "sbt.librarymanagement.BinaryFormats" ::
        "sbt.librarymanagement.ConstantFormats" ::
        "sbt.librarymanagement.PatchFormats" ::
        "sbt.librarymanagement.FullFormats" ::
        "sbt.librarymanagement.For3Use2_13Formats" ::
        "sbt.librarymanagement.For2_13Use3Formats" ::
        Nil
    }

    case "sbt.librarymanagement.ConfigRef" => { _ =>
      "sbt.librarymanagement.ConfigRefFormats" :: Nil
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
