object Compatibility {

  // patch method not available in sbt 0.13.8

  implicit class CrossVersionOps(val companion: sbt.CrossVersion.type) extends AnyVal {
    def patch: sbt.CrossVersion = new sbt.CrossVersion.Full(patchFun)
  }

  // adapted from sbt.CrossVersion from the sbt 0.13.16 sources

  private val BinCompatV = """(\d+)\.(\d+)\.(\d+)(-\w+)??-bin(-.*)?""".r

  private def patchFun(fullVersion: String): String =
    fullVersion match {
      case BinCompatV(x, y, z, w, _) => s"""$x.$y.$z${if (w == null) "" else w}"""
      case other                     => other
    }

}
