/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package graph
package rendering

import java.io.File
import java.net.{ URLEncoder, URI }

import sbt.io.IO

object DagreHTML {
  def createLink(dotGraph: String, targetDirectory: File): URI = {
    targetDirectory.mkdirs()
    val graphHTML = new File(targetDirectory, "graph.html")
    TreeView.saveResource("graph.html", graphHTML)
    IO.write(new File(targetDirectory, "dependencies.dot"), dotGraph, IO.utf8)

    val graphString =
      URLEncoder
        .encode(dotGraph, "utf8")
        .replace("+", "%20")

    IO.write(
      new File(targetDirectory, "dependencies.dot.js"),
      s"""data = "$graphString";""",
      IO.utf8
    )

    new URI(graphHTML.toURI.toString)
  }
}
