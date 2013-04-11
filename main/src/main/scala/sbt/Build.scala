/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import Keys.{name, organization, thisProject}
	import Def.{ScopedKey, Setting}

// name is more like BuildDefinition, but that is too long
trait Build
{
	def projectDefinitions(baseDirectory: File): Seq[Project] = projects
	def projects: Seq[Project] = ReflectUtilities.allVals[Project](this).values.toSeq
	def settings: Seq[Setting[_]] = Defaults.buildCore
	def buildLoaders: Seq[BuildLoader.Components] = Nil
	/** Explicitly defines the root project.
	* If None, the root project is the first project in the build's root directory or just the first project if none are in the root directory.*/
	def rootProject: Option[Project] = None
}
trait Plugin
{
	@deprecated("Override projectSettings or buildSettings instead.", "0.12.0")
	def settings: Seq[Setting[_]] = Nil

	/** Settings to be appended to all projects in a build. */
	def projectSettings: Seq[Setting[_]] = Nil

	/** Settings to be appended at the build scope. */
	def buildSettings: Seq[Setting[_]] = Nil

	/** Settings to be appended at the global scope. */
	def globalSettings: Seq[Setting[_]] = Nil
}

object Build
{
	val defaultEmpty: Build = new Build { override def projects = Nil }
	val default: Build = new Build { override def projectDefinitions(base: File) = defaultProject(base) :: Nil }
	def defaultAggregated(aggregate: Seq[ProjectRef]): Build = new Build {
		override def projectDefinitions(base: File) = defaultAggregatedProject(base, aggregate) :: Nil
	}

	def defaultID(base: File): String = "default-" + Hash.trimHashString(base.getAbsolutePath, 6)
	def defaultProject(base: File): Project = Project(defaultID(base), base).settings(
		// if the user has overridden the name, use the normal organization that is derived from the name.
		organization <<= (thisProject, organization, name) { (p, o, n) => if(p.id == n) "default" else o }
	)
	def defaultAggregatedProject(base: File, agg: Seq[ProjectRef]): Project =
		defaultProject(base).aggregate(agg : _*)

	@deprecated("Use Attributed.data", "0.13.0")
	def data[T](in: Seq[Attributed[T]]): Seq[T] = Attributed.data(in)
	def analyzed(in: Seq[Attributed[_]]): Seq[inc.Analysis] = in.flatMap{ _.metadata.get(Keys.analysis) }
}
