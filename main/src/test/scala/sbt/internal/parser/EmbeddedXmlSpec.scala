/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.parser

import sbt.internal.util.MessageOnlyException

class EmbeddedXmlSpec extends CheckIfParsedSpec {

  "File with xml content " should {

    "Handle last xml part" in {
      val errorLine = """<version>4.0<version>"""
      val buildSbt = s"""|
                         |
                         |name := "play-html-compressor"
                         |
                         |scalaVersion := "2.11.1"
                         |
                         |val pom = <xml:group><scm>
                         |<url>git@github.com:mhiva/play-html-compressor.git</url>
                         |<connection>scm:git:git@github.com:mohiva/play-html-compressor.git</connection>
                         |  </scm>
                         |<developers>
                         |    <developer>
                         |      <id>akkie</id>
                         |      <name>Christian Kaps</name>
                         |      <url>http://mohiva.com</url>
                         |    </developer>
                         |  </developers></xml:group>
                         |$errorLine
                         |
                         |""".stripMargin

      split(buildSbt) must throwA[MessageOnlyException].like {
        case exception =>
          val index = buildSbt.lines.indexWhere(line => line.contains(errorLine)) + 1
          val numberRegex = """(\d+)""".r
          val message = exception.getMessage
          val list = numberRegex.findAllIn(message).toList
          list must contain(index.toString)
      }

    }

  }

  protected val files = Seq(
    ("""
        |val p = <a/>
      """.stripMargin,
     "Xml modified closing tag at end of file",
     false,
     true),
    ("""
        |val p = <a></a>
      """.stripMargin,
     "Xml at end of file",
     false,
     true),
    ("""|
        |
        |name := "play-html-compressor"
        |
        |scalaVersion := "2.11.1"
        |
        |val lll = "</=+=>"
        |
        |val not = "<sss><sss>"
        |
        |val aaa = "ass/>"
        |
        |val pom = "</scm>"
        |
        |val aaa= <xml:group><scm><url>git@github.com:mohiva/play-html-compressor.git</url>
        |   <connection>scm:git:git@github.com:mohiva/play-html-compressor.git</connection>
        |  </scm>
        |  <developers>
        |    <developer>
        |      <id>akkie</id>
        |      <name>Christian Kaps</name>
        |      <url>http://mohiva.com</url>
        |    </developer>
        |  </developers>
        |  <version>4.0</version></xml:group>
        |
        |publishMavenStyle := true
        |
        |val anotherXml = <xml:group><a a="r"><bbb>
        |        content</bbb>
        |        <ccc atr="tre" />
        |        <aa/>
        |          </a></xml:group>
        |
        |val tra = "</scm>"
        |
      """.stripMargin,
     "Xml in string",
     false,
     true),
    ("""|
        |
        |name := "play-html-compressor"
        |
        |scalaVersion := "2.11.1"
        |
        |val ok = <ccc atr="tre" />
        |
        |val pom = <xml:group><scm>
        |<url>git@github.com:mhiva/play-html-compressor.git</url>
        |    <connection>scm:git:git@github.com:mohiva/play-html-compressor.git</connection>
        |</scm>
        |<developers>
        |<developer>
        |<id>akkie</id>
        |<name>Christian Kaps</name>
        |<url>http://mohiva.com</url>
        |</developer>
        |</developers>
        |<version>4.0</version></xml:group>
        |
        |publishMavenStyle := true
        |
        |val anotherXml = <xml:group><a a="r"><bbb>
        |        content</bbb>
        |        <ccc atr="tre" />
        |<aa/>
        | </a></xml:group>
        |
        | """.stripMargin,
     "Xml with attributes",
     false,
     true),
    (
      """
        |scalaVersion := "2.10.2"
        |
        |libraryDependencies += "org.scala-sbt" %% "sbinary" % "0.4.1" withSources() withJavadoc()
        |
        |lazy val checkPom = taskKey[Unit]("check pom to ensure no <type> sections are generated")
        |
        |checkPom := {
        |	val pomFile = makePom.value
        |	val pom = xml.XML.loadFile(pomFile)
        |	val tpe = pom \\ "type"
        |	if(!tpe.isEmpty)
        |		sys.error("Expected no <type> sections, got: " + tpe + " in \n\n" + pom)
        |}
        |
        |
        |val a = <aaa>
        |
        |</aaa>
        |
        |
        |
      """.stripMargin,
      "xml with blank line",
      false,
      true
    )
  )

}
