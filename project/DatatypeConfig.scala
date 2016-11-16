import sbt.datatype.{ CodecCodeGen, TpeRef }

object DatatypeConfig {

  /** Extract the only type parameter from a TpeRef */
  def oneArg(tpe: TpeRef): TpeRef = {
    val pat = s"""${CodecCodeGen.removeTypeParameters(tpe.name)}[<\\[](.+?)[>\\]]""".r
    val pat(arg0) = tpe.name
    TpeRef(arg0, false, false, false)
  }

  /** Extract the two type parameters from a TpeRef */
  def twoArgs(tpe: TpeRef): List[TpeRef] = {
    val pat = s"""${CodecCodeGen.removeTypeParameters(tpe.name)}[<\\[](.+?), (.+?)[>\\]]""".r
    val pat(arg0, arg1) = tpe.name
    TpeRef(arg0, false, false, false) :: TpeRef(arg1, false, false, false) :: Nil
  }

  /** Codecs that were manually written. */
  val myCodecs: PartialFunction[String, TpeRef => List[String]] = {
    case "java.util.Date" => { _ => "sbt.internal.librarymanagement.formats.DateFormat" :: Nil }

    case "scala.xml.NodeSeq" => { _ => "sbt.internal.librarymanagement.formats.NodeSeqFormat" :: Nil }

    case "org.apache.ivy.plugins.resolver.DependencyResolver" =>
      { _ => "sbt.internal.librarymanagement.formats.DependencyResolverFormat" :: Nil }

    case "xsbti.GlobalLock" => { _ => "sbt.internal.librarymanagement.formats.GlobalLockFormat" :: Nil }
    case "xsbti.Logger"     => { _ => "sbt.internal.librarymanagement.formats.LoggerFormat" :: Nil }

    case "sbt.librarymanagement.UpdateOptions" =>
      { _ => "sbt.internal.librarymanagement.formats.UpdateOptionsFormat" :: Nil }

    // TODO: These are handled by BasicJsonProtocol, and sbt-datatype should handle them by default, imo
    case "Option" | "Set"                  => { tpe => getFormats(oneArg(tpe)) }
    case "Map" | "Tuple2" | "scala.Tuple2" => { tpe => twoArgs(tpe).flatMap(getFormats) }
    case "Int" | "Long"                    => { _ => Nil }
  }

  /** Types for which we don't include the format -- they're just aliases to InclExclRule */
  val excluded = Set(
    "sbt.librarymanagement.InclusionRule",
    "sbt.librarymanagement.ExclusionRule")

  /** Returns the list of formats required to encode the given `TpeRef`. */
  val getFormats: TpeRef => List[String] =
    CodecCodeGen.extensibleFormatsForType {
      case TpeRef("sbt.internal.librarymanagement.RetrieveConfiguration", false, false, false) =>
        "sbt.librarymanagement.RetrieveConfigurationFormats" :: Nil
      case tpe @ TpeRef(name, _, _, _) if myCodecs isDefinedAt CodecCodeGen.removeTypeParameters(name) =>
        myCodecs(CodecCodeGen.removeTypeParameters(name))(tpe)
      case TpeRef(name, _, _, _) if excluded contains CodecCodeGen.removeTypeParameters(name) =>
        Nil
      case other =>
        CodecCodeGen.formatsForType(other)
    }

}
