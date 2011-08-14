import sbt._
import Keys._
import Load.{ BuildStructure, StructureIndex }
import scala.collection.{ mutable, immutable }

trait SbtCreateConfig {
  def name: String
  def organization: String
  def version: String
  def scalaVersion: String
}
object SbtCreateConfig {
  private def prop(propName: String, alt: String) = System.getProperty(propName) match {
    case null   => alt
    case value  => value
  }
  implicit def defaultProjectConfig = new SbtCreateConfig {
    def name         = prop("sbt-create.name", "project-name-here")
    def organization = prop("sbt-create.organization", "your.organization.here")
    def version      = prop("sbt-create.version", "0.1")
    def scalaVersion = prop("sbt-create.scalaVersion", "2.9.0-1")
  }
}

class TemplateBuild(implicit sbtCreateConfig: SbtCreateConfig) extends Build {
  // BuildStructure contains:
  // units:      Map[URI, LoadedBuildUnit]
  // root:       URI
  // settings:   Seq[Setting[_]]
  // data:       Settings[Scope]
  // index:      StructureIndex
  // streams:    Streams
  // delegates:  Scope => Seq[Scope]
  // scopeLocal: ScopeLocal

	private val cachedExtraction = new collection.mutable.HashMap[State, WState]
  private implicit def stateWrapper(state: State): WState =
    cachedExtraction.getOrElseUpdate(state, new WState(state))
  private implicit def revealStructure(state: State): BuildStructure =
    stateWrapper(state).structure
  private implicit def revealStructureIndex(state: State): StructureIndex =
    revealStructure(state).index
  private implicit def revealSession(state: State): SessionSettings =
    stateWrapper(state).session

  private class WState(state: State) {
    val extracted = Project extract state

    def projectId      = extracted.currentProject.id
    def structure      = extracted.structure
    def session        = extracted.session
    def currentRef     = extracted.currentRef
    def rootProject    = structure.rootProject
    def allProjects    = structure.allProjects

    def index          = structure.index
    def taskToKey      = index.taskToKey
    def keyMap         = index.keyMap       // Map[String, AttributeKey[_]]
    def keyIndex       = index.keyIndex
    def currentKeys    = keyIndex keys Some(currentRef) map index.keyMap
    def sortedKeys     = currentKeys.toSeq sortBy (_.label)
  }
  
  private class Tap[T](target: T) {
    def show(): Unit = target match {
      case xs: TraversableOnce[_] => xs foreach println
      case _                      => println(target)
    }
    def tap[U](f: T => U): T = {
      f(target)
      target
    }
  }
  private implicit def createTapper[T](target: T): Tap[T] = new Tap(target)

  def currentBranch = ("git status -sb".lines_! headOption) getOrElse "-" stripPrefix "## "
  
  val buildShellPrompt = { 
    (state: State) => "%s:%s>".format(
      state.projectId,
      currentBranch
    )
  }

  lazy val testSettings = Seq(
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2" % "1.5",
      "org.specs2" %% "specs2-scalaz-core" % "6.0.RC2" % "test"
    )
  )

  lazy val buildSettings = Seq(
    resolvers       += ScalaToolsSnapshots,
    organization    := "org.template",
    version         := "0.1-SNAPSHOT",
    scalaVersion    := "2.9.0-1",
    retrieveManaged := true,
    shellPrompt     := buildShellPrompt
    // logLevel     := Level.Debug,
  )

  lazy val templateConfig = Project(
    id           = "template",
    base         = file("."),
    aggregate    = Nil,
    dependencies = Nil,
    delegates    = Nil,
    settings     = Defaults.defaultSettings ++ buildSettings ++ Seq(
      commands += helpNames
    )
  )

  // A sample command definition.
  def helpNames = Command.command("help-names") { (state: State) =>
    state tap (_.sortedKeys map (_.label) show)
  }
}

object TemplateBuild extends TemplateBuild { }
