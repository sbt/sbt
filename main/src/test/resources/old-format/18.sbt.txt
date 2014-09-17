//
// Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

ScriptedPlugin.scriptedSettings

name := "sbt-osgi-manager"

description := "OSGi development bridge based on Bndtools and Tycho."

licenses := Seq("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

organization := "org.digimead"

organizationHomepage := Some(url("http://digimead.org"))

homepage := Some(url("https://github.com/digimead/sbt-osgi-manager"))

version <<= (baseDirectory) { (b) => scala.io.Source.fromFile(b / "version").mkString.trim }

// There is no "-Xfatal-warnings" because we have cross compilation against different Scala versions
scalacOptions ++= Seq("-encoding", "UTF-8", "-unchecked", "-deprecation", "-Xcheckinit")

// http://vanillajava.blogspot.ru/2012/02/using-java-7-to-target-much-older-jvms.html
javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation", "-source", "1.6", "-target", "1.6")

javacOptions in doc := Seq("-source", "1.6")

if (sys.env.contains("XBOOTCLASSPATH")) Seq(javacOptions += "-Xbootclasspath:" + sys.env("XBOOTCLASSPATH")) else Seq()

sbtPlugin := true

resourceGenerators in Compile <+=
  (resourceManaged in Compile, name, version) map { (dir, n, v) =>
    val file = dir / "version-%s.properties".format(n)
    val contents = "name=%s\nversion=%s\nbuild=%s\n".format(n, v, ((System.currentTimeMillis / 1000).toInt).toString)
    IO.write(file, contents)
    Seq(file)
  }

libraryDependencies ++= {
  val mavenVersion = "3.0" // based on Tycho, MUST be the same
  val mavenWagonVersion = "2.4"
  val tychoVersion = "0.18.0"
  val aetherAPIVersion = "1.7" // based on Tycho, MUST be the same
  Seq(
    "biz.aQute.bnd" % "bndlib" % "2.1.0",
    "org.apache.felix" % "org.apache.felix.resolver" % "1.0.0",
    "org.apache.maven" % "maven-aether-provider" % mavenVersion,
    "org.apache.maven" % "maven-artifact" % mavenVersion,
    "org.apache.maven" % "maven-compat" % mavenVersion,
    "org.apache.maven" % "maven-core" % mavenVersion,
    "org.apache.maven" % "maven-plugin-api" % mavenVersion,
    "org.apache.maven" % "maven-embedder" % mavenVersion, // provide org.apache.maven.cli.MavenCli
    "org.apache.maven.wagon" % "wagon-http" % mavenWagonVersion, // HTTP connector for remore repositories
    "org.apache.maven.wagon" % "wagon-file" % mavenWagonVersion, // File connector for local repositories
    "org.eclipse.tycho" % "tycho-core" % tychoVersion,
    "org.eclipse.tycho" % "tycho-p2-facade" % tychoVersion,
    "org.osgi" % "org.osgi.core" % "5.0.0",
    "org.osgi" % "org.osgi.enterprise" % "5.0.0",
    "org.sonatype.aether" % "aether-connector-wagon" % aetherAPIVersion
  )
}

scriptedBufferLog := false

resolvers ++= Seq(
  "sbt-osgi-mananger-digimead-maven" at "http://storage.googleapis.com/maven.repository.digimead.org/",
  Resolver.url("sbt-osgi-manager-typesafe-ivy-releases", url("http://repo.typesafe.com/typesafe/ivy-releases/"))(Resolver.defaultIvyPatterns),
  Resolver.url("sbt-osgi-manager-typesafe-ivy-snapshots", url("http://repo.typesafe.com/typesafe/ivy-snapshots/"))(Resolver.defaultIvyPatterns),
  Resolver.url("sbt-osgi-manager-typesafe-repository", url("http://typesafe.artifactoryonline.com/typesafe/ivy-releases/"))(Resolver.defaultIvyPatterns),
  Resolver.url("sbt-osgi-manager-typesafe-shapshots", url("http://typesafe.artifactoryonline.com/typesafe/ivy-snapshots/"))(Resolver.defaultIvyPatterns))

sourceGenerators in Compile <+= (sbtVersion, sourceDirectory in Compile, sourceManaged in Compile) map { (v, sourceDirectory, sourceManaged) =>
  val interface = v.split("""\.""").take(2).mkString(".")
  val source = sourceDirectory / ".." / "patch" / interface
  val generated = (PathFinder(source) ***) x Path.rebase(source, sourceManaged)
  IO.copy(generated, true, false)
  generated.map(_._2).filter(_.getName endsWith ".scala")
}

//logLevel := Level.Debug
