/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

/** Provides access to the current version of Scala being used to build a project.  These methods typically
* return None or the empty string when the loader is not used. */
object ScalaVersion
{
	/** The name of the system property containing the Scala version used for this project.*/
	private[sbt] val LiveKey = "sbt.scala.version"
	private[sbt] def crossString(v: String) = "scala_" + v
	/** Returns the current version of Scala being used to build the project, unless the sbt loader is not being used,
	* in which case this is the empty string.*/
	def currentString =
	{
		val v = System.getProperty(LiveKey)
		if(v == null)
			""
		else
			v.trim
	}
	/** Returns the current version of Scala being used to build the project.  If the sbt loader is not being
	* used, this returns None.  Otherwise, the value returned by this method is fixed for the duration of
	* a Project's existence.  It only changes on reboot (during which a Project is recreated).*/
	val current: Option[String] =
	{
		val sv = currentString
		if(sv.isEmpty)
			None
		else
			Some(sv)
	}
	private[sbt] def withCross[T](crossDisabled: Boolean)(withVersion: String => T, disabled: => T): T =
	{
		if(crossDisabled)
			disabled
		else
		{
			current match
			{
				case Some(scalaV) => withVersion(scalaV)
				case _ => disabled
			}
		}
	}
}