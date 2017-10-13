/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import Def.Setting
import java.io.File

/**
 * Represents the exported contents of a .sbt file.  Currently, that includes the list of settings,
 * the values of Project vals, and the import statements for all defined vals/defs.
 */
private[sbt] final class LoadedSbtFile(
    val settings: Seq[Setting[_]],
    val projects: Seq[Project],
    val importedDefs: Seq[String],
    val manipulations: Seq[Project => Project],
    // TODO - we may want to expose a simpler interface on top of here for the set command,
    // rather than what we have now...
    val definitions: DefinedSbtValues,
    val generatedFiles: Seq[File]
) {
  // We still use merge for now.  We track originating sbt file in an alternative manner.
  def merge(o: LoadedSbtFile): LoadedSbtFile =
    new LoadedSbtFile(
      settings ++ o.settings,
      projects ++ o.projects,
      importedDefs ++ o.importedDefs,
      manipulations,
      definitions zip o.definitions,
      generatedFiles ++ o.generatedFiles
    )

  def clearProjects =
    new LoadedSbtFile(settings, Nil, importedDefs, manipulations, definitions, generatedFiles)
}

/**
 * Represents the `val`/`lazy val` definitions defined within a build.sbt file
 * which we can reference in other settings.
 */
private[sbt] final class DefinedSbtValues(val sbtFiles: Seq[compiler.EvalDefinitions]) {

  def values(parent: ClassLoader): Seq[Any] =
    sbtFiles flatMap (_ values parent)

  def classloader(parent: ClassLoader): ClassLoader =
    sbtFiles.foldLeft(parent) { (cl, e) =>
      e.loader(cl)
    }

  def imports: Seq[String] = {
    // TODO - Sanity check duplicates and such, so users get a nice warning rather
    // than explosion.
    for {
      file <- sbtFiles
      m = file.enclosingModule
      v <- file.valNames
    } yield s"import ${m}.`${v}`"
  }
  def generated: Seq[File] =
    sbtFiles flatMap (_.generated)

  // Returns a classpath for the generated .sbt files.
  def classpath: Seq[File] =
    generated.map(_.getParentFile).distinct

  /**
   * Joins the defines of this build.sbt with another.
   *  TODO - we may want to figure out scoping rules, as this could lead to
   *  ambiguities.
   */
  def zip(other: DefinedSbtValues): DefinedSbtValues =
    new DefinedSbtValues(sbtFiles ++ other.sbtFiles)
}
private[sbt] object DefinedSbtValues {

  /** Construct a DefinedSbtValues object directly from the underlying representation. */
  def apply(eval: compiler.EvalDefinitions): DefinedSbtValues =
    new DefinedSbtValues(Seq(eval))

  /** Construct an empty value object. */
  def empty = new DefinedSbtValues(Nil)

}

private[sbt] object LoadedSbtFile {

  /** Represents an empty .sbt file: no Projects, imports, or settings.*/
  def empty = new LoadedSbtFile(Nil, Nil, Nil, Nil, DefinedSbtValues.empty, Nil)
}
