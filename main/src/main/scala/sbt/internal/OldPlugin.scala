package sbt
package internal

import Def.Setting

@deprecated("Use AutoPlugin", "0.13.8")
trait OldPlugin {
  /** Settings to be appended to all projects in a build. */
  def projectSettings: Seq[Setting[_]] = Nil

  /** Settings to be appended at the build scope. */
  def buildSettings: Seq[Setting[_]] = Nil

  /** Settings to be appended at the global scope. */
  def globalSettings: Seq[Setting[_]] = Nil
}
