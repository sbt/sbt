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
package rendering

import java.io.File
import java.net.{ URLEncoder, URI }

import net.virtualvoid.sbt.graph.util.IOUtil

object DagreHTML {
  def createLink(dotGraph: String, targetDirectory: File): URI = {
    targetDirectory.mkdirs()
    val graphHTML = new File(targetDirectory, "graph.html")
    IOUtil.saveResource("graph.html", graphHTML)
    IOUtil.writeToFile(dotGraph, new File(targetDirectory, "dependencies.dot"))

    val graphString =
      URLEncoder.encode(dotGraph, "utf8")
        .replaceAllLiterally("+", "%20")

    IOUtil.writeToFile(s"""data = "$graphString";""", new File(targetDirectory, "dependencies.dot.js"))

    new URI(graphHTML.toURI.toString)
  }
}
