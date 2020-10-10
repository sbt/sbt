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
            |<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/3.5.17/d3.min.js"></script>
            |<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
            |<script src="https://www.samsarin.com/project/graphlib-dot/v0.6.1/graphlib-dot.js"></script>
            |<script src="https://www.samsarin.com/project/dagre-d3/v0.4.16/dagre-d3.min.js"></script>
            |<script src="dependencies.dot.js"></script>
            |
            |<style>
            |    body {
            |        margin: 0;
            |        overflow: hidden;
            |    }
            |    .node {
            |        white-space: nowrap;
            |    }
            |
            |    .node rect,
            |    .node circle,
            |    .node ellipse {
            |        stroke: #333;
            |        fill: #fff;
            |        stroke-width: 1.5px;
            |    }
            |
            |    .cluster rect {
            |        stroke: #333;
            |        fill: #000;
            |        fill-opacity: 0.1;
            |        stroke-width: 1.5px;
            |    }
            |
            |    .edgePath path.path {
            |        stroke: #333;
            |        stroke-width: 1.5px;
            |        fill: none;
            |    }
            |</style>
            |
            |<style>
            |    h1, h2 {
            |        color: #333;
            |    }
            |</style>
            |
            |<body onLoad="initialize()">
            |
            |<svg width=1280 height=1024>
            |    <g/>
            |</svg>
            |
            |<script>
            |function initialize() {
            |    // Set up zoom support
            |    var svg = d3.select("svg"),
            |        inner = d3.select("svg g"),
            |        zoom = d3.behavior.zoom().on("zoom", function() {
            |          inner.attr("transform", "translate(" + d3.event.translate + ")" +
            |                                      "scale(" + d3.event.scale + ")");
            |        });
            |    svg.attr("width", window.innerWidth);
            |
            |    svg.call(zoom);
            |    // Create and configure the renderer
            |    var render = dagreD3.render();
            |    function tryDraw(inputGraph) {
            |      var g;
            |      {
            |        g = graphlibDot.read(inputGraph);
            |        g.graph().rankdir = "LR";
            |        d3.select("svg g").call(render, g);
            |
            |        // Center the graph
            |        var initialScale = 0.10;
            |        zoom
            |          .translate([(svg.attr("width") - g.graph().width * initialScale) / 2, 20])
            |          .scale(initialScale)
            |          .event(svg);
            |        svg.attr('height', g.graph().height * initialScale + 40);
            |      }
            |    }
            |    tryDraw(decodeURIComponent(data));
            |}
            |</script>
            |</body>
            |
          """.stripMargin

        val html : String = scala.io.Source.fromFile(htmlFile).mkString
        val errors = compareByLine(html, expectedHtml)
        require(errors.isEmpty , errors.mkString("\n"))
        ()
      }
    )

def compareByLine(got : String, expected : String) : Seq[String] = {
  val errors = ListBuffer[String]()
  got.split("\n").zip(expected.split("\n").toSeq).zipWithIndex.foreach { case((got_line : String, expected_line : String), i : Int) =>
    if(got_line != expected_line) {
      errors.append(
        """not matching lines at line %s
          |expected: %s
          |got:      %s
          |""".stripMargin.format(i,expected_line, got_line))
    }
  }
  errors
}
