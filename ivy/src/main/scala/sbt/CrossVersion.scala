package sbt

import cross.CrossVersionUtil

final case class ScalaVersion(full: String, binary: String)

sealed trait CrossVersion
object CrossVersion
{
	val TransitionScalaVersion = CrossVersionUtil.TransitionScalaVersion
	val TransitionSbtVersion = CrossVersionUtil.TransitionSbtVersion

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

	@deprecated("Use CrossVersion.isScalaApiCompatible or CrossVersion.isSbtApiCompatible", "0.13.0")
	def isStable(v: String): Boolean = isScalaApiCompatible(v)
	@deprecated("Use CrossVersion.scalaApiVersion or CrossVersion.sbtApiVersion", "0.13.0")
	def selectVersion(full: String, binary: String): String = if(isStable(full)) binary else full
	def isSbtApiCompatible(v: String): Boolean = CrossVersionUtil.isSbtApiCompatible(v)
	/** Returns sbt binary interface x.y API compatible with the given version string v. 
	 * RCs for x.y.0 are considered API compatible.
	 * Compatibile versions include 0.12.0-1 and 0.12.0-RC1 for Some(0, 12).
	 */
	def sbtApiVersion(v: String): Option[(Int, Int)] = CrossVersionUtil.sbtApiVersion(v)
	def isScalaApiCompatible(v: String): Boolean = CrossVersionUtil.isScalaApiCompatible(v)
	/** Returns Scala binary interface x.y API compatible with the given version string v. 
	 * Compatibile versions include 2.10.0-1 and 2.10.1-M1 for Some(2, 10), but not 2.10.0-RC1.
	 */
	def scalaApiVersion(v: String): Option[(Int, Int)] = CrossVersionUtil.scalaApiVersion(v)
	val PartialVersion = CrossVersionUtil.PartialVersion
	def partialVersion(s: String): Option[(Int,Int)] = CrossVersionUtil.partialVersion(s)
	def binaryScalaVersion(full: String): String = CrossVersionUtil.binaryScalaVersion(full)
	def binarySbtVersion(full: String): String = CrossVersionUtil.binarySbtVersion(full)
	@deprecated("Use CrossVersion.scalaApiVersion or CrossVersion.sbtApiVersion", "0.13.0")
	def binaryVersion(full: String, cutoff: String): String = CrossVersionUtil.binaryVersion(full, cutoff)

}
