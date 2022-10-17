import scala.collection.mutable.ListBuffer

ThisBuild / scalaVersion := "2.9.2"
ThisBuild / version := "0.1-SNAPSHOT"

lazy val justATransiviteDependencyEndpointProject = project

lazy val justATransitiveDependencyProject = project
  .dependsOn(justATransiviteDependencyEndpointProject)

lazy val justADependencyProject = project

lazy val test_project = project
  .dependsOn(justADependencyProject, justATransitiveDependencyProject)
  .settings(
    TaskKey[Unit]("check") := {
      val htmlFile = (dependencyBrowseGraphHTML in Compile).value
      val expectedHtml =
        """<!doctype html>
          |
          |<!--
          |
          |Based on https://github.com/cpettitt/dagre-d3/blob/d215446e7e40ebfca303f4733e746e96420e3b46/demo/interactive-demo.html
          |which is published under this license:
          |
          |Copyright (c) 2013 Chris Pettitt
          |
          |Permission is hereby granted, free of charge, to any person obtaining a copy
          |of this software and associated documentation files (the "Software"), to deal
          |in the Software without restriction, including without limitation the rights
          |to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
          |copies of the Software, and to permit persons to whom the Software is
          |furnished to do so, subject to the following conditions:
          |
          |The above copyright notice and this permission notice shall be included in
          |all copies or substantial portions of the Software.
          |
          |THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
          |IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
          |FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
          |AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
          |LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
          |OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
          |THE SOFTWARE.
          |
          |-->
          |
          |<meta charset="utf-8">
          |<title>Dependency Graph</title>
          |
          |<script src="https://d3js.org/d3.v5.min.js"></script>
          |<script src="https://unpkg.com/@hpcc-js/wasm@0.3.15/dist/index.min.js"></script>
          |<script src="https://unpkg.com/d3-graphviz@3.2.0/build/d3-graphviz.js"></script>
          |<script src="dependencies.dot.js"></script>
          |
          |<body>
          |<div id="graph"></div>
          |</body>
          |
          |<script>
          |    d3.select("#graph").graphviz().renderDot(decodeURIComponent(data));
          |</script>
          |
          """.stripMargin

      val html: String = scala.io.Source.fromFile(htmlFile).mkString
      val errors = compareByLine(html, expectedHtml)
      require(errors.isEmpty, errors.mkString("\n"))
      ()
    }
  )

def compareByLine(got: String, expected: String): Seq[String] = {
  val errors = ListBuffer[String]()
  got.split("\n").zip(expected.split("\n").toSeq).zipWithIndex.foreach {
    case ((got_line: String, expected_line: String), i: Int) =>
      if (got_line != expected_line) {
        errors.append("""not matching lines at line %s
          |expected: %s
          |got:      %s
          |""".stripMargin.format(i, expected_line, got_line))
      }
  }
  errors
}
