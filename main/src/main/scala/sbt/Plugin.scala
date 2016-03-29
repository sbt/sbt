package sbt

import Def.Setting

// TODO 0.14.0: decide if Plugin should be deprecated in favor of AutoPlugin
trait Plugin {
  @deprecated("Override projectSettings or buildSettings instead.", "0.12.0")
  def settings: Seq[Setting[_]] = Nil

  /** Settings to be appended to all projects in a build. */
  def projectSettings: Seq[Setting[_]] = Nil

  /** Settings to be appended at the build scope. */
  def buildSettings: Seq[Setting[_]] = Nil

  /** Settings to be appended at the global scope. */
  def globalSettings: Seq[Setting[_]] = Nil
}
