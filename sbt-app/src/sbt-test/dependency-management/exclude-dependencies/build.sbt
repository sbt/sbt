import scala.xml.{ Node, _ }
import scala.xml.Utility.trim
import sbt.internal.librarymanagement.{ IvySbt, MakePom }

lazy val check = taskKey[Unit]("check")

val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"
val repatchTwitter = "com.eed3si9n" %% "repatch-twitter-core" % "dispatch0.11.1_0.1.0"

lazy val a = (project in file("a")).
  settings(
    scalaVersion := "2.11.12",
    libraryDependencies += dispatch,
    excludeDependencies += "org.slf4j"
  )

lazy val b = (project in file("b")).
  settings(
    scalaVersion := "2.11.12",
    libraryDependencies += repatchTwitter,
    excludeDependencies += "net.databinder.dispatch" %% "dispatch-core"
  )

lazy val root = (project in file(".")).
  settings(
    check := {
      (a / update).value
      (b / update).value
      val acp = (a / Compile / externalDependencyClasspath).value.sortBy {_.data.getName}
      val bcp = (b / Compile / externalDependencyClasspath).value.sortBy {_.data.getName}

      if (acp exists { _.data.getName contains "slf4j-api-1.7.5.jar" }) {
        sys.error("slf4j-api-1.7.5.jar found when it should NOT be included: " + acp.toString)
      }
      if (bcp exists { _.data.getName contains "dispatch-core_2.11-0.11.1.jar" }) {
        sys.error("dispatch-core_2.11-0.11.1.jar found when it should NOT be included: " + bcp.toString)
      }

      val bPomXml = makePomXml(streams.value.log, (b / makePomConfiguration).value, (b / ivyModule).value)

      val repatchTwitterXml = bPomXml \ "dependencies" \ "dependency" find { d =>
        (d \ "groupId").text == "com.eed3si9n" && (d \ "artifactId").text == "repatch-twitter-core_2.11"
      } getOrElse (sys error s"Missing repatch-twitter-core dependency: $bPomXml")

      val excludeDispatchCoreXml =
        <exclusion>
          <groupId>net.databinder.dispatch</groupId>
          <artifactId>dispatch-core_2.11</artifactId>
        </exclusion>

      if (trim((repatchTwitterXml \ "exclusions" \ "exclusion").head) != trim(excludeDispatchCoreXml))
        sys error s"Missing dispatch-core exclusion: $repatchTwitterXml"

      ()
    }
  )

def makePomXml(log: Logger, makePomConfig: MakePomConfiguration, ivyModule: IvySbt#Module): Node = {
  ivyModule.withModule[Node](log) { (ivy, md, default) =>
    import makePomConfig._
    new MakePom(log).toPom(
      ivy, md, moduleInfo.get, configurations, includeTypes, extra.get, filterRepositories, allRepositories)
  }
}
