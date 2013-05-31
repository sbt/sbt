package sbt

final case class ScalaVersion(full: String, binary: String)

sealed trait CrossVersion
object CrossVersion
{
	val TransitionScalaVersion = "2.10"
	val TransitionSbtVersion = "0.12"

	object Disabled extends CrossVersion { override def toString = "disabled" }
	final class Binary(val remapVersion: String => String) extends CrossVersion {
		override def toString = "Binary"
	}
	final class Full(val remapVersion: String => String) extends CrossVersion {
		override def toString = "Full"
	}

	def full: CrossVersion = new Full(idFun)
	def fullMapped(remapVersion: String => String): CrossVersion = new Full(remapVersion)

	def binary: CrossVersion = new Binary(idFun)
	def binaryMapped(remapVersion: String => String): CrossVersion = new Full(remapVersion)

	private[this] def idFun[T]: T => T = x => x
	def append(s: String): Option[String => String]  =  Some(x => crossName(x, s))

	def apply(cross: CrossVersion, fullVersion: String, binaryVersion: String): Option[String => String] =
		cross match
		{
			case Disabled => None
			case b: Binary => append(b.remapVersion(binaryVersion))
			case f: Full => append(f.remapVersion(fullVersion))
		}

	def apply(module: ModuleID, is: IvyScala): Option[String => String] =
		CrossVersion(module.crossVersion, is.scalaFullVersion, is.scalaBinaryVersion)

	def apply(module: ModuleID, is: Option[IvyScala]): Option[String => String] =
		is flatMap { i => apply(module, i) }

	def substituteCross(artifacts: Seq[Artifact], cross: Option[String => String]): Seq[Artifact] =
		cross match {
			case None => artifacts
			case Some(is) => substituteCrossA(artifacts, cross)
		}

	def applyCross(s: String, fopt: Option[String => String]): String =
		fopt match {
			case None => s
			case Some(fopt) => fopt(s)
		}

	def crossName(name: String, cross: String): String =
		name + "_" + cross
	def substituteCross(a: Artifact, cross: Option[String => String]): Artifact =
		a.copy(name = applyCross(a.name, cross))
	def substituteCrossA(as: Seq[Artifact], cross: Option[String => String]): Seq[Artifact] =
		as.map(art => substituteCross(art, cross))

	def apply(scalaFullVersion: String, scalaBinaryVersion: String): ModuleID => ModuleID = m =>
	{
		val cross = apply(m.crossVersion, scalaFullVersion, scalaBinaryVersion)
		if(cross.isDefined)
			m.copy(name = applyCross(m.name, cross), explicitArtifacts = substituteCrossA(m.explicitArtifacts, cross))
		else
			m
	}

	def isStable(v: String): Boolean = !v.contains("-")
	def selectVersion(full: String, binary: String): String = if(isStable(full)) binary else full

	val PartialVersion = """(\d+)\.(\d+)(?:\..+)?""".r
	def partialVersion(s: String): Option[(Int,Int)] =
		s match {
			case PartialVersion(major, minor) => Some(major.toInt, minor.toInt)
			case _ => None
		}
	private[this] def isNewer(major: Int, minor: Int, minMajor: Int, minMinor: Int): Boolean =
		major > minMajor || (major == minMajor && minor >= minMinor)
	
	def binaryScalaVersion(full: String): String = binaryVersion(full, TransitionScalaVersion)
	def binarySbtVersion(full: String): String = binaryVersion(full, TransitionSbtVersion)

	private[sbt] def binarySbtVersionFuture(full: String): String = sbtApiVersion(full) match {
		case Some((x,y)) => x + "." + y
		case None => full
	}
	private[this] def sbtApiVersion(v: String): Option[(Int, Int)] =
	{
		val ReleaseV = """(\d+)\.(\d+)\.(\d+)(-\d+)?""".r
		val CandidateV = """(\d+)\.(\d+)\.(\d+)(-RC\d+)""".r
		val NonReleaseV = """(\d+)\.(\d+)\.(\d+)(-\w+)""".r
		v match {
			// before 0.12, the full sbt version was used
			case PartialVersion(x, y) if (x.toInt == 0 && y.toInt < 12)  => None
			// 0.12 uses x.y always, regardless of release status
			case PartialVersion(x, y) if (x.toInt == 0 && y.toInt == 12) => Some((x.toInt, y.toInt))
			// 0.13 and later behavior
			case ReleaseV(x, y, z, ht)    => Some((x.toInt, y.toInt))
			case CandidateV(x, y, z, ht)  => Some((x.toInt, y.toInt))
			case NonReleaseV(x, y, z, ht) if z.toInt > 0 => Some((x.toInt, y.toInt))
			// otherwise, use the full version
			case _ => None
		}
	}

	def binaryVersion(full: String, cutoff: String): String =
	{
		def sub(major: Int, minor: Int) = major + "." + minor
		(partialVersion(full), partialVersion(cutoff)) match {
			case (Some((major, minor)), None) => sub(major, minor)
			case (Some((major, minor)), Some((minMajor, minMinor))) if isNewer(major, minor, minMajor, minMinor) => sub(major, minor)
			case _ => full
		}
	}
}

