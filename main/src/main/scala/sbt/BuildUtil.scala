package sbt

import java.net.URI

final class BuildUtil[Proj](
    val keyIndex: KeyIndex,
    val data: Settings[Scope],
    val root: URI,
    val rootProjectID: URI => String,
    val project: (URI, String) => Proj,
    val configurations: Proj => Seq[ConfigKey],
    val aggregates: Relation[ProjectRef, ProjectRef]) {
  def rootProject(uri: URI): Proj =
    project(uri, rootProjectID(uri))

  def resolveRef(ref: Reference): ResolvedReference =
    Scope.resolveReference(root, rootProjectID, ref)

  def projectFor(ref: ResolvedReference): Proj = ref match {
    case ProjectRef(uri, id) => project(uri, id)
    case BuildRef(uri)       => rootProject(uri)
  }
  def projectRefFor(ref: ResolvedReference): ProjectRef = ref match {
    case p: ProjectRef => p
    case BuildRef(uri) => ProjectRef(uri, rootProjectID(uri))
  }
  def projectForAxis(ref: Option[ResolvedReference]): Proj = ref match {
    case Some(ref) => projectFor(ref)
    case None      => rootProject(root)
  }
  def exactProject(refOpt: Option[Reference]): Option[Proj] = refOpt map resolveRef flatMap {
    case ProjectRef(uri, id) => Some(project(uri, id))
    case _                   => None
  }

  val configurationsForAxis: Option[ResolvedReference] => Seq[String] =
    refOpt => configurations(projectForAxis(refOpt)).map(_.name)
}
object BuildUtil {
  def apply(root: URI, units: Map[URI, LoadedBuildUnit], keyIndex: KeyIndex, data: Settings[Scope]): BuildUtil[ResolvedProject] =
    {
      val getp = (build: URI, project: String) => Load.getProject(units, build, project)
      val configs = (_: ResolvedProject).configurations.map(c => ConfigKey(c.name))
      val aggregates = aggregationRelation(units)
      new BuildUtil(keyIndex, data, root, Load getRootProject units, getp, configs, aggregates)
    }

  def dependencies(units: Map[URI, LoadedBuildUnit]): BuildDependencies =
    {
      import collection.mutable.HashMap
      val agg = new HashMap[ProjectRef, Seq[ProjectRef]]
      val cp = new HashMap[ProjectRef, Seq[ClasspathDep[ProjectRef]]]
      for (lbu <- units.values; rp <- lbu.defined.values) {
        val ref = ProjectRef(lbu.unit.uri, rp.id)
        cp(ref) = rp.dependencies
        agg(ref) = rp.aggregate
      }
      BuildDependencies(cp.toMap, agg.toMap)
    }

  def checkCycles(units: Map[URI, LoadedBuildUnit]) {
    def getRef(pref: ProjectRef) = units(pref.build).defined(pref.project)
    def deps(proj: ResolvedProject)(base: ResolvedProject => Seq[ProjectRef]): Seq[ResolvedProject] = Dag.topologicalSort(proj)(p => base(p) map getRef)
    // check for cycles
    for ((_, lbu) <- units; proj <- lbu.defined.values) {
      deps(proj)(_.dependencies.map(_.project))
      deps(proj)(_.delegates)
      deps(proj)(_.aggregate)
    }
  }

  def baseImports: Seq[String] = "import sbt._, Keys._, dsl._" :: Nil

  def getImports(unit: BuildUnit): Seq[String] = unit.plugins.detected.imports

  @deprecated("Use getImports(Seq[String]).", "0.13.2")
  def getImports(pluginNames: Seq[String], buildNames: Seq[String]): Seq[String] = getImports(pluginNames ++ buildNames)

  /** `import sbt._, Keys._`, and wildcard import `._` for all names. */
  def getImports(names: Seq[String]): Seq[String] = baseImports ++ importAllRoot(names)

  /** Import just the names. */
  def importNames(names: Seq[String]): Seq[String] = if (names.isEmpty) Nil else names.mkString("import ", ", ", "") :: Nil
  /** Prepend `_root_` and import just the names. */
  def importNamesRoot(names: Seq[String]): Seq[String] = importNames(names map rootedName)

  /** Wildcard import `._` for all values. */
  def importAll(values: Seq[String]): Seq[String] = importNames(values map { _ + "._" })
  def importAllRoot(values: Seq[String]): Seq[String] = importAll(values map rootedName)
  def rootedName(s: String): String = if (s contains '.') "_root_." + s else s

  def aggregationRelation(units: Map[URI, LoadedBuildUnit]): Relation[ProjectRef, ProjectRef] =
    {
      val depPairs =
        for {
          (uri, unit) <- units.toIterable
          project <- unit.defined.values
          ref = ProjectRef(uri, project.id)
          agg <- project.aggregate
        } yield (ref, agg)
      Relation.empty ++ depPairs
    }
}
