package sbt

import Def.Setting

/**
 * Represents the exported contents of a .sbt file.  Currently, that includes the list of settings,
 * the values of Project vals, and the import statements for all defined vals/defs.
 */
private[sbt] final class LoadedSbtFile(
    val settings: Seq[Setting[_]],
    val projects: Seq[Project],
    val importedDefs: Seq[String],
    val manipulations: Seq[Project => Project]) {
  @deprecated("LoadedSbtFiles are no longer directly merged.", "0.13.6")
  def merge(o: LoadedSbtFile): LoadedSbtFile =
    new LoadedSbtFile(settings ++ o.settings, projects ++ o.projects, importedDefs ++ o.importedDefs, manipulations)

  def clearProjects = new LoadedSbtFile(settings, Nil, importedDefs, manipulations)
}
private[sbt] object LoadedSbtFile {
  /** Represents an empty .sbt file: no Projects, imports, or settings.*/
  def empty = new LoadedSbtFile(Nil, Nil, Nil, Nil)
}

