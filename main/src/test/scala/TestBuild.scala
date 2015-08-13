package sbt

import Def.{ ScopedKey, Setting }
import Types.{ const, idFun, some }
import complete.Parser

import java.io.File
import java.net.URI
import org.scalacheck._
import Prop._
import Gen._
import Arbitrary.arbBool

// Notes:
//  Generator doesn't produce cross-build project dependencies or do anything with the 'extra' axis
object TestBuild {
  val MaxTasks = 6
  val MaxProjects = 7
  val MaxConfigs = 5
  val MaxBuilds = 4
  val MaxIDSize = 8
  val MaxDeps = 8
  val KeysPerEnv = 10

  val MaxTasksGen = chooseShrinkable(1, MaxTasks)
  val MaxProjectsGen = chooseShrinkable(1, MaxProjects)
  val MaxConfigsGen = chooseShrinkable(1, MaxConfigs)
  val MaxBuildsGen = chooseShrinkable(1, MaxBuilds)
  val MaxDepsGen = chooseShrinkable(0, MaxDeps)

  def chooseShrinkable(min: Int, max: Int): Gen[Int] =
    sized(sz => choose(min, (max min sz) max 1))

  implicit val cGen = Arbitrary { genConfigs(idGen, MaxDepsGen, MaxConfigsGen) }
  implicit val tGen = Arbitrary { genTasks(idGen, MaxDepsGen, MaxTasksGen) }

  final class Keys(val env: Env, val scopes: Seq[Scope]) {
    override def toString = env + "\n" + scopes.mkString("Scopes:\n\t", "\n\t", "")
    lazy val delegated = scopes map env.delegates
  }

  final case class Structure(env: Env, current: ProjectRef, data: Settings[Scope], keyIndex: KeyIndex, keyMap: Map[String, AttributeKey[_]]) {
    override def toString = env.toString + "\n" + "current: " + current + "\nSettings:\n\t" + showData + keyMap.keys.mkString("All keys:\n\t", ", ", "")
    def showKeys(map: AttributeMap): String = map.keys.mkString("\n\t   ", ",", "\n")
    def showData: String =
      {
        val scopeStrings =
          for ((scope, map) <- data.data) yield (Scope.display(scope, "<key>"), showKeys(map))
        scopeStrings.toSeq.sorted.map(t => t._1 + t._2).mkString("\n\t")
      }
    val extra: BuildUtil[Proj] =
      {
        val getp = (build: URI, project: String) => env.buildMap(build).projectMap(project)
        new BuildUtil(keyIndex, data, env.root.uri, env.rootProject, getp, _.configurations.map(c => ConfigKey(c.name)), Relation.empty)
      }

    lazy val allAttributeKeys: Set[AttributeKey[_]] = data.data.values.flatMap(_.keys).toSet
    lazy val (taskAxes, globalTaskAxis, onlyTaskAxis, multiTaskAxis) =
      {
        import collection.{ breakOut, mutable }
        import mutable.HashSet

        // task axis of Scope is set to Global and the value of the second map is the original task axis
        val taskAxesMappings =
          for ((scope, keys) <- data.data.toIterable; key <- keys.keys) yield (ScopedKey(scope.copy(task = Global), key), scope.task): (ScopedKey[_], ScopeAxis[AttributeKey[_]])

        val taskAxes = Relation.empty ++ taskAxesMappings
        val global = new HashSet[ScopedKey[_]]
        val single = new HashSet[ScopedKey[_]]
        val multi = new HashSet[ScopedKey[_]]
        for ((skey, tasks) <- taskAxes.forwardMap) {
          def makeKey(task: ScopeAxis[AttributeKey[_]]) = ScopedKey(skey.scope.copy(task = task), skey.key)
          val hasGlobal = tasks(Global)
          if (hasGlobal)
            global += skey
          else {
            val keys = tasks map makeKey
            if (keys.size == 1)
              single ++= keys
            else if (keys.size > 1)
              multi ++= keys
          }
        }
        (taskAxes, global.toSet, single.toSet, multi.toSet)
      }
  }
  final class Env(val builds: Seq[Build], val tasks: Seq[Taskk]) {
    override def toString = "Env:\n  " + "  Tasks:\n    " + tasks.mkString("\n    ") + "\n" + builds.mkString("\n  ")
    val root = builds.head
    val buildMap = mapBy(builds)(_.uri)
    val taskMap = mapBy(tasks)(getKey)
    def project(ref: ProjectRef) = buildMap(ref.build).projectMap(ref.project)
    def projectFor(ref: ResolvedReference) = ref match { case pr: ProjectRef => project(pr); case BuildRef(uri) => buildMap(uri).root }

    lazy val allProjects = builds.flatMap(_.allProjects)
    def rootProject(uri: URI): String = buildMap(uri).root.id
    def inheritConfig(ref: ResolvedReference, config: ConfigKey) = projectFor(ref).confMap(config.name).extended map toConfigKey
    def inheritTask(task: AttributeKey[_]) = taskMap.get(task) match { case None => Nil; case Some(t) => t.delegates map getKey }
    def inheritProject(ref: ProjectRef) = project(ref).delegates
    def resolve(ref: Reference) = Scope.resolveReference(builds.head.uri, rootProject, ref)
    lazy val delegates: Scope => Seq[Scope] =
      Scope.delegates(
        allProjects,
        (_: Proj).configurations.map(toConfigKey),
        resolve,
        uri => buildMap(uri).root.id,
        inheritProject,
        inheritConfig,
        inheritTask,
        (ref, mp) => Nil
      )
    lazy val allFullScopes: Seq[Scope] =
      for {
        (ref, p) <- (Global, root.root) +: allProjects.map { case (ref, p) => (Select(ref), p) }
        t <- Global +: tasks.map(t => Select(t.key))
        c <- Global +: p.configurations.map(c => Select(ConfigKey(c.name)))
      } yield Scope(project = ref, config = c, task = t, extra = Global)
  }
  def getKey: Taskk => AttributeKey[_] = _.key
  def toConfigKey: Config => ConfigKey = c => ConfigKey(c.name)
  final class Build(val uri: URI, val projects: Seq[Proj]) {
    override def toString = "Build " + uri.toString + " :\n    " + projects.mkString("\n    ")
    val allProjects = projects map { p => (ProjectRef(uri, p.id), p) }
    val root = projects.head
    val projectMap = mapBy(projects)(_.id)
  }
  final class Proj(val id: String, val delegates: Seq[ProjectRef], val configurations: Seq[Config]) {
    override def toString = "Project " + id + "\n      Delegates:\n        " + delegates.mkString("\n        ") +
      "\n      Configurations:\n        " + configurations.mkString("\n        ")
    val confMap = mapBy(configurations)(_.name)
  }

  final class Config(val name: String, val extended: Seq[Config]) {
    override def toString = name + " (extends: " + extended.map(_.name).mkString(", ") + ")"
  }
  final class Taskk(val key: AttributeKey[String], val delegates: Seq[Taskk]) {
    override def toString = key.label + " (delegates: " + delegates.map(_.key.label).mkString(", ") + ")"
  }

  def mapBy[K, T](s: Seq[T])(f: T => K): Map[K, T] = s map { t => (f(t), t) } toMap;

  implicit lazy val arbKeys: Arbitrary[Keys] = Arbitrary(keysGen)
  lazy val keysGen: Gen[Keys] = for (env <- mkEnv; keyCount <- chooseShrinkable(1, KeysPerEnv); keys <- listOfN(keyCount, scope(env))) yield new Keys(env, keys)

  def scope(env: Env): Gen[Scope] =
    for {
      build <- oneOf(env.builds)
      project <- oneOf(build.projects)
      cAxis <- oneOrGlobal(project.configurations map toConfigKey)
      tAxis <- oneOrGlobal(env.tasks map getKey)
      pAxis <- orGlobal(frequency((1, BuildRef(build.uri)), (3, ProjectRef(build.uri, project.id))))
    } yield Scope(pAxis, cAxis, tAxis, Global)

  def orGlobal[T](gen: Gen[T]): Gen[ScopeAxis[T]] =
    frequency((1, gen map Select.apply), (1, Global))
  def oneOrGlobal[T](gen: Seq[T]): Gen[ScopeAxis[T]] = orGlobal(oneOf(gen))

  def makeParser(structure: Structure): Parser[ScopedKey[_]] =
    {
      import structure._
      def confs(uri: URI) = env.buildMap.get(uri).toList.flatMap { _.root.configurations.map(_.name) }
      val defaultConfs: Option[ResolvedReference] => Seq[String] = {
        case None                  => confs(env.root.uri)
        case Some(BuildRef(uri))   => confs(uri)
        case Some(ref: ProjectRef) => env.project(ref).configurations.map(_.name)
      }
      Act.scopedKey(keyIndex, current, defaultConfs, keyMap, data)
    }

  def structure(env: Env, settings: Seq[Setting[_]], current: ProjectRef): Structure =
    {
      implicit val display = Def.showRelativeKey(current, env.allProjects.size > 1)
      val data = Def.make(settings)(env.delegates, const(Nil), display)
      val keys = data.allKeys((s, key) => ScopedKey(s, key))
      val keyMap = keys.map(k => (k.key.label, k.key)).toMap[String, AttributeKey[_]]
      val projectsMap = env.builds.map(b => (b.uri, b.projects.map(_.id).toSet)).toMap
      new Structure(env, current, data, KeyIndex(keys, projectsMap), keyMap)
    }

  implicit lazy val mkEnv: Gen[Env] =
    {
      implicit val cGen = genConfigs(idGen, MaxDepsGen, MaxConfigsGen)
      implicit val tGen = genTasks(idGen, MaxDepsGen, MaxTasksGen)
      implicit val pGen = (uri: URI) => genProjects(uri)(idGen, MaxDepsGen, MaxProjectsGen, cGen)
      envGen(buildGen(uriGen, pGen), tGen)
    }

  implicit def maskGen(implicit arbBoolean: Arbitrary[Boolean]): Gen[ScopeMask] =
    {
      val b = arbBoolean.arbitrary
      for (p <- b; c <- b; t <- b; x <- b) yield ScopeMask(project = p, config = c, task = t, extra = x)
    }

  implicit lazy val idGen: Gen[String] = for (size <- chooseShrinkable(1, MaxIDSize); cs <- listOfN(size, alphaChar)) yield cs.mkString
  implicit lazy val optIDGen: Gen[Option[String]] = frequency((1, idGen map some.fn), (1, None))
  implicit lazy val uriGen: Gen[URI] = for (sch <- idGen; ssp <- idGen; frag <- optIDGen) yield new URI(sch, ssp, frag.orNull)

  implicit def envGen(implicit bGen: Gen[Build], tasks: Gen[Seq[Taskk]]): Gen[Env] =
    for (i <- MaxBuildsGen; bs <- listOfN(i, bGen); ts <- tasks) yield new Env(bs, ts)
  implicit def buildGen(implicit uGen: Gen[URI], pGen: URI => Gen[Seq[Proj]]): Gen[Build] = for (u <- uGen; ps <- pGen(u)) yield new Build(u, ps)

  def nGen[T](igen: Gen[Int])(implicit g: Gen[T]): Gen[List[T]] = igen flatMap { ig => listOfN(ig, g) }

  implicit def genProjects(build: URI)(implicit genID: Gen[String], maxDeps: Gen[Int], count: Gen[Int], confs: Gen[Seq[Config]]): Gen[Seq[Proj]] =
    genAcyclic(maxDeps, genID, count) { (id: String) =>
      for (cs <- confs) yield { (deps: Seq[Proj]) =>
        new Proj(id, deps.map { dep => ProjectRef(build, dep.id) }, cs)
      }
    }
  def genConfigs(implicit genName: Gen[String], maxDeps: Gen[Int], count: Gen[Int]): Gen[Seq[Config]] =
    genAcyclicDirect[Config, String](maxDeps, genName, count)((key, deps) => new Config(key, deps))
  def genTasks(implicit genName: Gen[String], maxDeps: Gen[Int], count: Gen[Int]): Gen[Seq[Taskk]] =
    genAcyclicDirect[Taskk, String](maxDeps, genName, count)((key, deps) => new Taskk(AttributeKey[String](key), deps))

  def genAcyclicDirect[A, T](maxDeps: Gen[Int], keyGen: Gen[T], max: Gen[Int])(make: (T, Seq[A]) => A): Gen[Seq[A]] =
    genAcyclic[A, T](maxDeps, keyGen, max) { t =>
      Gen.const { deps =>
        make(t, deps)
      }
    }

  def genAcyclic[A, T](maxDeps: Gen[Int], keyGen: Gen[T], max: Gen[Int])(make: T => Gen[Seq[A] => A]): Gen[Seq[A]] =
    max flatMap { count =>
      listOfN(count, keyGen) flatMap { keys =>
        genAcyclic(maxDeps, keys.distinct)(make)
      }
    }
  def genAcyclic[A, T](maxDeps: Gen[Int], keys: List[T])(make: T => Gen[Seq[A] => A]): Gen[Seq[A]] =
    genAcyclic(maxDeps, keys, Nil) flatMap { pairs =>
      sequence(pairs.map { case (key, deps) => mapMake(key, deps, make) }) flatMap { inputs =>
        val made = new collection.mutable.HashMap[T, A]
        for ((key, deps, mk) <- inputs)
          made(key) = mk(deps map made)
        keys map made
      }
    }

  def mapMake[A, T](key: T, deps: Seq[T], make: T => Gen[Seq[A] => A]): Gen[Inputs[A, T]] =
    make(key) map { (mk: Seq[A] => A) => (key, deps, mk) }

  def genAcyclic[T](maxDeps: Gen[Int], names: List[T], acc: List[Gen[(T, Seq[T])]]): Gen[Seq[(T, Seq[T])]] =
    names match {
      case Nil => sequence(acc)
      case x :: xs =>
        val next = for (depCount <- maxDeps; d <- pick(depCount min xs.size, xs)) yield (x, d.toList)
        genAcyclic(maxDeps, xs, next :: acc)
    }
  def sequence[T](gs: Seq[Gen[T]]): Gen[Seq[T]] = Gen.parameterized { prms =>
    wrap(gs map { g => g(prms) getOrElse sys.error("failed generator") })
  }
  type Inputs[A, T] = (T, Seq[T], Seq[A] => A)
}
