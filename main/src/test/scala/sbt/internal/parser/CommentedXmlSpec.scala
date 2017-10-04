/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.parser

class CommentedXmlSpec extends CheckIfParsedSpec {

  override protected val files = Seq(
    (
      s"""|
         |val pom = "</scm>"
         |
         |val aaa= <scm><url>git@a.com:a/a.git</url>
         |    <cc>e</cc>
         |  </scm>
         |
         |val tra = "</scm>"
         |
       """.stripMargin,
      "Xml in string",
      false,
      true
    ),
    ("""
        |val scmpom = taskKey[xml.NodeBuffer]("Node buffer")
        |
        |scmpom := <scm>
        |    <url>git@github.com:mohiva/play-html-compressor.git</url>
        |    <connection>scm:git:git@github.com:mohiva/play-html-compressor.git</connection>
        |  </scm>
        | <developers>
        |    <developer>
        |      <id>akkie</id>
        |      <name>Christian Kaps</name>
        |      <url>http://mohiva.com</url>
        |    </developer>
        |  </developers>
        |  //<aaa/>
        |  <a></a>
        |
        |publishMavenStyle := true
        |
      """.stripMargin,
     "Wrong Commented xml ",
     false,
     true),
    ("""
        |val scmpom = taskKey[xml.NodeBuffer]("Node buffer")
        |
        |scmpom := <scm>
        |    <url>git@github.com:mohiva/play-html-compressor.git</url>
        |    <connection>scm:git:git@github.com:mohiva/play-html-compressor.git</connection>
        |  </scm>
        | <developers>
        |    <developer>
        |      <id>akkie</id>
        |      <name>Christian Kaps</name>
        |      <url>http://mohiva.com</url>
        |    </developer>
        |  </developers>
        |  //<aaa/>
        |  //<a></a>
        |
        |publishMavenStyle := true
        |
      """.stripMargin,
     "Commented xml ",
     false,
     true),
    ("""
        |import sbt._
        |
        |// </a
      """.stripMargin,
     "Xml in comment",
     true,
     false),
    ("""
        |// a/>
      """.stripMargin,
     "Xml in comment2",
     false,
     false)
  )
}
