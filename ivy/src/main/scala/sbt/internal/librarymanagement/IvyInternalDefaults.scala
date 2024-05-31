package sbt
package internal.librarymanagement

import java.io.File
import sbt.librarymanagement.ivy._
import sbt.io.syntax._
import xsbti.{ Logger => XLogger }
import sbt.util.Logger

/**
 * This is a list of functions with default values.
 */
object IvyInternalDefaults {
  def defaultBaseDirectory: File =
    (new File(".")).getAbsoluteFile / "lib_managed"

  def getBaseDirectory(opt: Option[File]): File =
    opt.getOrElse(defaultBaseDirectory)

  def getLog(opt: Option[XLogger]): XLogger =
    opt.getOrElse(Logger.Null)

  def defaultIvyPaths: IvyPaths =
    IvyPaths(defaultBaseDirectory.toString, None)

  def getIvyPaths(opt: Option[IvyPaths]): IvyPaths =
    opt.getOrElse(defaultIvyPaths)
}
