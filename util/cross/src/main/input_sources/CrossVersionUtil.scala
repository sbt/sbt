package ${{cross.package0}}.${{cross.package1}}

object CrossVersionUtil
{
	val trueString = "true"
	val falseString = "false"
	val fullString = "full"
	val noneString = "none"
	val disabledString = "disabled"
	val binaryString = "binary"
	val TransitionDottyVersion = "" // Dotty always respects binary compatibility
	val TransitionScalaVersion = "2.10" // ...but scalac doesn't until Scala 2.10
	val TransitionSbtVersion = "0.12"

	def isFull(s: String): Boolean = (s == trueString) || (s == fullString)
	def isDisabled(s: String): Boolean = (s == falseString) || (s == noneString) || (s == disabledString)
	def isBinary(s: String): Boolean = (s == binaryString)

	private lazy val intPattern = """\d{1,10}"""
	private lazy val basicVersion = """(""" + intPattern + """)\.(""" + intPattern + """)\.(""" + intPattern + """)"""

	private[${{cross.package0}}] def isSbtApiCompatible(v: String): Boolean = sbtApiVersion(v).isDefined
	/** Returns sbt binary interface x.y API compatible with the given version string v. 
	 * RCs for x.y.0 are considered API compatible.
	 * Compatibile versions include 0.12.0-1 and 0.12.0-RC1 for Some(0, 12).
	 */
	private[${{cross.package0}}] def sbtApiVersion(v: String): Option[(Int, Int)] =
	{
		val ReleaseV = (basicVersion + """(-\d+)?""").r
		val CandidateV = (basicVersion + """(-RC\d+)""").r
		val NonReleaseV = (basicVersion + """([-\w+]*)""").r
		v match {
			case ReleaseV(x, y, z, ht)   => Some(sbtApiVersion(x.toInt, y.toInt))
			case CandidateV(x, y, z, ht) => Some(sbtApiVersion(x.toInt, y.toInt))
			case NonReleaseV(x, y, z, ht) if x.toInt == 0 && z.toInt > 0 =>
				Some(sbtApiVersion(x.toInt, y.toInt))
			case NonReleaseV(x, y, z, _) if x.toInt > 0 && (y.toInt > 0 || z.toInt > 0) =>
				Some(sbtApiVersion(x.toInt, y.toInt))
			case _ => None
		}
	}

  private[${{cross.package0}}] def sbtApiVersion(x: Int, y: Int): (Int, Int) = {
    // Prior to sbt 1 the "sbt api version" was the X.Y in the X.Y.Z version.
    // For example for sbt 0.13.x releases, the sbt api version is 0.13
    // As of sbt 1 it is now X.0.
    // This means, for example, that all versions of sbt 1.x have sbt api version 1.0
    if (x > 0) (x, 0)
    else (x, y)
  }

	private[${{cross.package0}}] def isScalaApiCompatible(v: String): Boolean = scalaApiVersion(v).isDefined
	/** Returns Scala binary interface x.y API compatible with the given version string v. 
	 * Compatibile versions include 2.10.0-1 and 2.10.1-M1 for Some(2, 10), but not 2.10.0-RC1.
	 */
	private[${{cross.package0}}] def scalaApiVersion(v: String): Option[(Int, Int)] =
	{
		val ReleaseV = (basicVersion + """(-\d+)?""").r
		val BinCompatV = (basicVersion + """-bin(-.*)?""").r
		val NonReleaseV = (basicVersion + """(-\w+)""").r
		v match {
			case ReleaseV(x, y, z, ht)    => Some((x.toInt, y.toInt))
			case BinCompatV(x, y, z, ht)    => Some((x.toInt, y.toInt))
			case NonReleaseV(x, y, z, ht) if z.toInt > 0 => Some((x.toInt, y.toInt))
			case _ => None
		}
	}
	private[${{cross.package0}}] val PartialVersion = ("""(""" + intPattern + """)\.(""" + intPattern + """)(?:\..+)?""").r
	private[${{cross.package0}}] def partialVersion(s: String): Option[(Int, Int)] =
		s match {
			case PartialVersion(major, minor) => Some((major.toInt, minor.toInt))
			case _ => None
		}
	def binaryScalaVersion(full: String): String = {
		val cutoff =
			if (full.startsWith("0."))
				TransitionDottyVersion
			else
				TransitionScalaVersion

		binaryVersionWithApi(full, cutoff)(scalaApiVersion)
	}
	def binarySbtVersion(full: String): String = binaryVersionWithApi(full, TransitionSbtVersion)(sbtApiVersion)
	private[${{cross.package0}}] def binaryVersion(full: String, cutoff: String): String = binaryVersionWithApi(full, cutoff)(scalaApiVersion)
	private[this] def isNewer(major: Int, minor: Int, minMajor: Int, minMinor: Int): Boolean =
		major > minMajor || (major == minMajor && minor >= minMinor)
	private[this] def binaryVersionWithApi(full: String, cutoff: String)(apiVersion: String => Option[(Int, Int)]): String =
	{
		def sub(major: Int, minor: Int) = major + "." + minor
		(apiVersion(full), partialVersion(cutoff)) match {
			case (Some((major, minor)), None) => sub(major, minor)
			case (Some((major, minor)), Some((minMajor, minMinor))) if isNewer(major, minor, minMajor, minMinor) => sub(major, minor)
			case _ => full
		}
	}
}
