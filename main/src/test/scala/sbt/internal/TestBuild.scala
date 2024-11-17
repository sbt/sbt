/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import Def.{ ScopedKey, Setting }
import sbt.internal.util.{ AttributeKey, Relation }
import sbt.internal.util.Types.{ const, some }
import sbt.internal.util.complete.Parser
import sbt.librarymanagement.Configuration

import java.net.URI

import hedgehog._
import hedgehog.predef.sequence

object TestBuild extends TestBuild
abstract class TestBuild {
  val MaxTasks = 6
  val MaxProjects = 7
  val MaxConfigs = 5
  val MaxBuilds = 4
  val MaxIDSize = 8
  val MaxDeps = 8
  val KeysPerEnv = 10

  val MaxTasksGen = Range.linear(1, MaxTasks)
  val MaxProjectsGen = Range.linear(1, MaxProjects)
  val MaxConfigsGen = Range.linear(1, MaxConfigs)
  val MaxBuildsGen = Range.linear(1, MaxBuilds)
  val MaxDepsGen = Range.linear(0, MaxDeps)
  val MaxIDSizeGen = Range.linear(0, MaxIDSize)

  def alphaLowerChar: Gen[Char] = Gen.char('a', 'z')
  def alphaUpperChar: Gen[Char] = Gen.char('A', 'Z')
  def numChar: Gen[Char] = Gen.char('0', '9')
  def alphaNumChar: Gen[Char] =
    Gen.frequency1(8 -> alphaLowerChar, 1 -> alphaUpperChar, 1 -> numChar)

  val nonEmptyId = for {
    c <- alphaLowerChar
    cs <- Gen.list(alphaNumChar, MaxIDSizeGen)
  } yield (c :: cs).mkString

  def cGen = genConfigs(using nonEmptyId map { _.capitalize }, MaxDepsGen, MaxConfigsGen)
  def tGen = genTasks(using kebabIdGen, MaxDepsGen, MaxTasksGen)

  class TestKeys(val env: Env, val scopes: Seq[Scope]) {
    override def toString = env.toString + "\n" + scopes.mkString("Scopes:\n\t", "\n\t", "")
    lazy val delegated = scopes map env.delegates
  }

  sealed case class Structure(
      env: Env,
      current: ProjectRef,
      data: Def.Settings,
      keyIndex: KeyIndex,
      keyMap: Map[String, AttributeKey[?]]
  ) {
    override def toString =
      env.toString + "\n" + "current: " + current + "\nSettings:\n\t" + showData + keyMap.keys
        .mkString("All keys:\n\t", ", ", "")
    def showKeys(keys: Iterable[AttributeKey[?]]): String = keys.mkString("\n\t   ", ",", "\n")
    def showData: String = {
      val scopeStrings =
        for (scope, keys) <- data.keys.groupMap(_.scope)(_.key)
        yield (Scope.display(scope, "<key>"), showKeys(keys))
      scopeStrings.toSeq.sorted.map(t => t._1 + t._2).mkString("\n\t")
    }
    val extra: BuildUtil[Proj] = {
      val getp = (build: URI, project: String) => env.buildMap(build).projectMap(project)
      new BuildUtil(
        keyIndex,
        data,
        env.root.uri,
        env.rootProject,
        getp,
        _.configurations.map(c => ConfigKey(c.name)),
        Relation.empty
      )
    }

    lazy val allAttributeKeys: Set[AttributeKey[?]] = {
      if (data.attributeKeys.isEmpty) {
        sys.error("allAttributeKeys is empty")
      }
      data.attributeKeys
    }
    lazy val (taskAxes, zeroTaskAxis, onlyTaskAxis, multiTaskAxis) = {
      import collection.mutable
      import mutable.HashSet

      // task axis of Scope is set to Zero and the value of the second map is the original task axis
      val taskAxesMappings =
        for
          (scope, keys) <- data.keys.groupMap(_.scope)(_.key)
          key <- keys
        yield (ScopedKey(scope.copy(task = Zero), key), scope.task)

      val taskAxes = Relation.empty ++ taskAxesMappings
      val zero = new HashSet[ScopedKey[?]]
      val single = new HashSet[ScopedKey[?]]
      val multi = new HashSet[ScopedKey[?]]
      for ((skey, tasks) <- taskAxes.forwardMap) {
        def makeKey(task: ScopeAxis[AttributeKey[?]]) =
          ScopedKey(skey.scope.copy(task = task), skey.key)
        val hasGlobal = tasks(Zero)
        if (hasGlobal) zero += skey
        else {
          val keys = tasks map makeKey
          keys.size match {
            case 0 =>
            case 1 => single ++= keys
            case _ => multi ++= keys
          }
        }
      }
      (taskAxes, zero.toSet, single.toSet, multi.toSet)
    }
  }
  case class Env(builds: Vector[Build], tasks: Vector[Taskk]) {
    override def toString =
      "Env:\n  " + "  Tasks:\n    " + tasks.mkString("\n    ") + "\n" + builds.mkString("\n  ")
    val root = builds.head
    val buildMap = mapBy(builds)(_.uri)
    val taskMap = mapBy(tasks)(getKey)
    def project(ref: ProjectRef) = buildMap(ref.build).projectMap(ref.project)
    def projectFor(ref: ResolvedReference) = ref match {
      case pr: ProjectRef => project(pr); case BuildRef(uri) => buildMap(uri).root
    }

    lazy val allProjects = builds.flatMap(_.allProjects)
    def rootProject(uri: URI): String = buildMap(uri).root.id
    def inheritConfig(ref: ResolvedReference, config: ConfigKey) =
      projectFor(ref).confMap(config.name).extendsConfigs map toConfigKey
    def inheritTask(task: AttributeKey[?]) = taskMap.get(task) match {
      case None    => Vector()
      case Some(t) => t.delegates.toVector map getKey
    }
    def inheritProject(ref: ProjectRef) = project(ref).delegates.toVector
    def resolve(ref: Reference) = Scope.resolveReference(root.uri, rootProject, ref)
    lazy val delegates: Scope => Seq[Scope] =
      Scope.delegates(
        allProjects,
        (_: Proj).configurations.toVector.map(toConfigKey),
        resolve,
        uri => buildMap(uri).root.id,
        inheritProject,
        inheritConfig,
        inheritTask,
      )
    lazy val allFullScopes: Seq[Scope] =
      for {
        (ref, p) <- (Zero, root.root) +: allProjects.map { case (ref, p) => (Select(ref), p) }
        t <- Zero +: tasks.map(t => Select(t.key))
        c <- Zero +: p.configurations.map(c => Select(ConfigKey(c.name)))
      } yield Scope(project = ref, config = c, task = t, extra = Zero)
  }
  def getKey: Taskk => AttributeKey[?] = _.key
  def toConfigKey: Configuration => ConfigKey = c => ConfigKey(c.name)
  case class Build(uri: URI, projects: Seq[Proj]) {
    override def toString = "Build " + uri.toString + " :\n    " + projects.mkString("\n    ")
    val allProjects = projects map { p =>
      (ProjectRef(uri, p.id), p)
    }
    val root = projects.head
    val projectMap = mapBy(projects)(_.id)
  }
  case class Proj(
      id: String,
      delegates: Seq[ProjectRef],
      configurations: Seq[Configuration]
  ) {
    override def toString =
      "Project " + id + "\n      Delegates:\n        " + delegates.mkString("\n        ") +
        "\n      Configurations:\n        " + configurations.mkString("\n        ")
    val confMap = mapBy(configurations)(_.name)
  }

  case class Taskk(key: AttributeKey[String], delegates: Seq[Taskk]) {
    override def toString =
      key.label + " (delegates: " + delegates.map(_.key.label).mkString(", ") + ")"
  }

  def mapBy[K, T](s: Seq[T])(f: T => K): Map[K, T] =
    s.map(t => (f(t), t)).toMap

  lazy val keysGen: Gen[TestKeys] =
    for {
      env <- mkEnv
      keys <- scope(env).list(Range.linear(1, KeysPerEnv))
    } yield new TestKeys(env, keys)

  def scope(env: Env): Gen[Scope] =
    for {
      build <- oneOf(env.builds)
      project <- oneOf(build.projects)
      cAxis <- oneOrGlobal(project.configurations map toConfigKey)
      tAxis <- oneOrGlobal(env.tasks map getKey)
      pAxis <- orGlobal(
        Gen.frequency1(
          (1, Gen.constant[Reference](BuildRef(build.uri))),
          (3, Gen.constant[Reference](ProjectRef(build.uri, project.id)))
        )
      )
    } yield Scope(pAxis, cAxis, tAxis, Zero)

  def orGlobal[T](gen: Gen[T]): Gen[ScopeAxis[T]] =
    Gen.frequency1((1, gen map Select.apply), (1, Gen.constant(Zero)))
  def oneOrGlobal[T](gen: Seq[T]): Gen[ScopeAxis[T]] = orGlobal(oneOf(gen))

  def makeParser(structure: Structure): Parser[ScopedKey[?]] = {
    import structure._
    def confs(uri: URI) =
      env.buildMap.get(uri).toList.flatMap { _.root.configurations.map(_.name) }
    val defaultConfs: Option[ResolvedReference] => Seq[String] = {
      case None                  => confs(env.root.uri)
      case Some(BuildRef(uri))   => confs(uri)
      case Some(ref: ProjectRef) => env.project(ref).configurations.map(_.name)
    }
    Act.scopedKey(keyIndex, current, defaultConfs, keyMap, data)
  }

  def structure(env: Env, settings: Seq[Setting[?]], current: ProjectRef): Structure = {
    val display = Def.showRelativeKey2(current)
    if (settings.isEmpty) {
      try {
        sys.error("settings is empty")
      } catch {
        case e: Throwable =>
          e.printStackTrace
          throw e
      }
    }
    val data = Def.makeWithCompiledMap(settings)(using env.delegates, const(Nil), display)._2
    val keyMap = data.keys.map(k => (k.key.label, k.key)).toMap[String, AttributeKey[?]]
    val projectsMap = env.builds.map(b => (b.uri, b.projects.map(_.id).toSet)).toMap
    val confs = for {
      b <- env.builds
      p <- b.projects
    } yield p.id -> p.configurations
    val confMap = confs.toMap
    Structure(env, current, data, KeyIndex(data.keys, projectsMap, confMap), keyMap)
  }

  lazy val mkEnv: Gen[Env] = {
    val pGen = (uri: URI) => genProjects(uri)(nonEmptyId, MaxDepsGen, MaxProjectsGen, cGen)
    envGen(buildGen(uriGen, pGen), tGen)
  }

  def maskGen: Gen[ScopeMask] = {
    val b = Gen.boolean
    for (p <- b; c <- b; t <- b; x <- b)
      yield ScopeMask(project = p, config = c, task = t, extra = x)
  }

  val kebabIdGen: Gen[String] = for {
    c <- alphaLowerChar
    cs <- Gen.list(
      Gen.frequency(MaxIDSize -> alphaNumChar, List(1 -> Gen.constant('-'))),
      Range.linear(0, MaxIDSize - 2)
    )
    end <- alphaNumChar
  } yield (List(c) ++ cs ++ List(end)).mkString

  val optIDGen: Gen[Option[String]] =
    Gen.choice1(nonEmptyId.map(some[String]), Gen.constant(None))

  val pathGen = for {
    c <- alphaLowerChar
    cs <- Gen.list(alphaNumChar, Range.linear(6, MaxIDSize))
  } yield (c :: cs).mkString

  val uriGen: Gen[URI] = {
    for {
      ssp <- pathGen
      frag <- optIDGen
    } yield new URI("file", "///" + ssp + "/", frag.orNull)
  }

  def envGen(bGen: Gen[Build], tasks: Gen[Vector[Taskk]]): Gen[Env] =
    for (bs <- bGen.list(MaxBuildsGen).map(_.toVector); ts <- tasks)
      yield new Env(bs, ts)
  def buildGen(uGen: Gen[URI], pGen: URI => Gen[Vector[Proj]]): Gen[Build] =
    for (u <- uGen; ps <- pGen(u)) yield new Build(u, ps)

  def nGen[T](igen: Gen[Int])(g: Gen[T]): Gen[Vector[T]] = igen flatMap { ig =>
    g.list(Range.linear(ig, ig)).map(_.toVector)
  }

  def genProjects(build: URI)(
      genID: Gen[String],
      maxDeps: Range[Int],
      count: Range[Int],
      confs: Gen[Vector[Configuration]]
  ): Gen[Vector[Proj]] =
    genAcyclic(maxDeps, genID, count) { (id: String) =>
      for (cs <- confs) yield { (deps: Seq[Proj]) =>
        new Proj(
          id,
          deps.map { dep =>
            ProjectRef(build, dep.id)
          },
          cs
        )
      }
    }

  def genConfigs(using
      genName: Gen[String],
      maxDeps: Range[Int],
      count: Range[Int]
  ): Gen[Vector[Configuration]] =
    genAcyclicDirect[Configuration, String](maxDeps, genName, count)((key, deps) =>
      Configuration
        .of(key.capitalize, key)
        .withExtendsConfigs(deps.toVector)
    )

  def genTasks(using
      genName: Gen[String],
      maxDeps: Range[Int],
      count: Range[Int]
  ): Gen[Vector[Taskk]] =
    genAcyclicDirect[Taskk, String](maxDeps, genName, count)((key, deps) =>
      new Taskk(AttributeKey[String](key), deps)
    )

  def genAcyclicDirect[A, T](maxDeps: Range[Int], keyGen: Gen[T], max: Range[Int])(
      make: (T, Vector[A]) => A
  ): Gen[Vector[A]] =
    genAcyclic[A, T](maxDeps, keyGen, max) { t =>
      Gen.constant { deps =>
        make(t, deps.toVector)
      }
    }

  def genAcyclic[A, T](maxDeps: Range[Int], keyGen: Gen[T], max: Range[Int])(
      make: T => Gen[Vector[A] => A]
  ): Gen[Vector[A]] = {
    keyGen.list(max) flatMap { keys =>
      genAcyclic(maxDeps, keys.distinct.toVector)(make)
    }
  }
  def genAcyclic[A, T](maxDeps: Range[Int], keys: Vector[T])(
      make: T => Gen[Vector[A] => A]
  ): Gen[Vector[A]] =
    genAcyclic(maxDeps, keys, Vector()) flatMap { pairs =>
      sequence(pairs.map { case (key, deps) => mapMake(key, deps, make) }.toList) map { inputs =>
        val made = new collection.mutable.HashMap[T, A]
        for ((key, deps, mk) <- inputs)
          made(key) = mk(deps map made)
        keys map made
      }
    }

  def mapMake[A, T](key: T, deps: Vector[T], make: T => Gen[Vector[A] => A]): Gen[Inputs[A, T]] =
    make(key) map { (mk: Vector[A] => A) =>
      (key, deps, mk)
    }

  def genAcyclic[T](
      maxDeps: Range[Int],
      names: Vector[T],
      acc: Vector[Gen[(T, Vector[T])]]
  ): Gen[Vector[(T, Vector[T])]] =
    names match {
      case Vector() => sequence(acc.toList).map(_.toVector)
      case Vector(x, xs @ _*) =>
        val next =
          for (depCount <- Gen.int(maxDeps); d <- pick(depCount, xs))
            yield (x, d.toVector)
        genAcyclic(maxDeps, xs.toVector, next +: acc)
    }

  type Inputs[A, T] = (T, Vector[T], Vector[A] => A)

  def oneOf[A](a: Seq[A]): Gen[A] =
    Gen.element(a.head, a.tail.toList)

  // TODO Should move to hedgehog possible?
  def pick[A](n: Int, as: Seq[A]): Gen[Seq[A]] = {
    if (n >= as.length) {
      Gen.constant(as)
    } else {
      def go(m: Int, bs: Set[Int], cs: Set[Int]): Gen[Set[Int]] =
        if (m == 0)
          Gen.constant(cs)
        else
          Gen.element(bs.head, bs.tail.toList).flatMap(a => go(m - 1, bs - a, cs + a))
      go(n, as.indices.toSet, Set())
        .map(is => as.zipWithIndex.flatMap(a => if (is(a._2)) Seq(a._1) else Seq()))
    }
  }
}
