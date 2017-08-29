/*
 * Copyright 2015 Johannes Rudolph
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package sbt

import sbt.compat.SbtCompat._
import librarymanagement._
import internal._
import librarymanagement._
import Classpaths._
import LibraryManagement._
import internal.util.JLine

/** Accessors to private[sbt] symbols. */
object SbtAccess {
  val unmanagedScalaInstanceOnly = Defaults.unmanagedScalaInstanceOnly

  def getTerminalWidth: Int = JLine.usingTerminal(_.getWidth)

}
