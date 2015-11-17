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

package net.virtualvoid.sbt.graph

import java.io.File

import net.virtualvoid.sbt.graph.backend.IvyReport

object Main extends App {
  def die(msg: String): Nothing = {
    println(msg)
    sys.exit(1)
  }
  def usage: String =
    "Usage: <ivy-report-file> <output-file>"

  val reportFile = args.lift(0).filter(f ⇒ new File(f).exists).getOrElse(die(usage))
  val outputFile = args.lift(1).getOrElse(die(usage))
  val graph = IvyReport.fromReportFile(reportFile)
  rendering.GraphML.saveAsGraphML(graph, outputFile)
}
