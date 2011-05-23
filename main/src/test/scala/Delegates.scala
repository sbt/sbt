package sbt

	import Project._
	import Types.{idFun,some}

	import java.io.File
	import java.net.URI
	import org.scalacheck._
	import Prop._
	import Gen._

// Notes:
//  Generator doesn't produce cross-build project dependencies or do anything with the 'extra' axis
object Delegates extends Properties("delegates")
{
	val MaxTasks = 10
	val MaxProjects = 10
	val MaxConfigs = 10
	val MaxBuilds = 10
	val MaxIDSize = 10
	val MaxDeps = 10
	val KeysPerEnv = 25
	
	val MaxTasksGen = chooseShrinkable(1, MaxTasks)
	val MaxProjectsGen = chooseShrinkable(1, MaxProjects)
	val MaxConfigsGen = chooseShrinkable(1, MaxConfigs)
	val MaxBuildsGen = chooseShrinkable(1, MaxBuilds)
	val MaxDepsGen = chooseShrinkable(0, MaxDeps)

	def chooseShrinkable(min: Int, max: Int): Gen[Int] =
		sized( sz => choose(min, (max min sz) max 1) )

	implicit val cGen = Arbitrary { genConfigs(idGen, MaxDepsGen, MaxConfigsGen) }
	implicit val tGen = Arbitrary { genTasks(idGen, MaxDepsGen, MaxTasksGen) }

	property("generate non-empty configs") = forAll { (c: Seq[Config]) => !c.isEmpty }
	property("generate non-empty tasks") = forAll { (t: Seq[Task]) => !t.isEmpty }

	property("no duplicate scopes") = forAll { (keys: Keys) =>
		allDelegates(keys) { (_, ds) =>  ds.distinct.size == ds.size }
	}
	property("delegates non-empty") = forAll { (keys: Keys) =>
		allDelegates(keys) { (_, ds) => !ds.isEmpty }
	}

	property("An initially Global axis is Global in all delegates") = allAxes(alwaysGlobal)

	property("Projects precede builds precede Global") = forAll { (keys: Keys) =>
		allDelegates(keys) { (scope, ds) =>
			val projectAxes = ds.map(_.project)
			val nonProject = projectAxes.dropWhile { case Select(_: ProjectRef) => true; case _ => false }
			val global = nonProject.dropWhile { case Select(_: BuildRef) => true; case _ => false }
			global forall { _ == Global }
		}
	}
	property("Initial scope present with all combinations of Global axes") = allAxes(globalCombinations)

	property("initial scope first") = forAll { (keys: Keys) =>
		allDelegates(keys) { (scope, ds) => ds.head == scope }
	}
	property("global scope last") = forAll { (keys: Keys) =>
		allDelegates(keys) { (_, ds) => ds.last == Scope.GlobalScope }
	}


	def allAxes(f: (Scope, Seq[Scope], Scope => ScopeAxis[_]) => Prop): Prop = forAll { (keys: Keys) =>
		allDelegates(keys) { (s, ds) =>
			all( f(s, ds, _.project), f(s, ds, _.config), f(s, ds, _.task), f(s, ds, _.extra) )
		}
	}
	def allDelegates(keys: Keys)(f: (Scope, Seq[Scope]) => Prop): Prop = all( keys.scopes map { scope =>
		val delegates = keys.env.delegates(scope)
		("Scope: " + Scope.display(scope, "_")) |:
		("Delegates:\n\t" + delegates.map( scope => Scope.display(scope, "_") ).mkString("\n\t")) |:
		f(scope, delegates)
	} : _*)
	def alwaysGlobal(s: Scope, ds: Seq[Scope], axis: Scope => ScopeAxis[_]): Prop =
		(axis(s) != Global) ||
			all( ds map { d => (axis(d) == Global) : Prop } : _*)
	def globalCombinations(s: Scope, ds: Seq[Scope], axis: Scope => ScopeAxis[_]): Prop =
	{
		val value = axis(s)
		val mods = List[Scope => Scope](_.copy(project = Global), _.copy(config = Global), _.copy(task = Global), _.copy(extra = Global) )
		val modAndIdent = mods.map(_ :: idFun[Scope] :: Nil)

		def loop(cur: Scope, acc: List[Scope], rem: List[Seq[Scope => Scope]]): Seq[Scope] =
			rem match
			{
				case Nil => acc
				case x :: xs => x flatMap { mod =>
					val s = mod(cur)
					loop(s, s :: acc, xs)
				}
			}
		all( loop(s, Nil, modAndIdent).map( ds contains _ : Prop) : _*)
	}

	final class Keys(val env: Env, val scopes: Seq[Scope])
	{
		override def toString = env + "\n" + scopes.mkString("Scopes:\n\t", "\n\t", "")
		val delegated = scopes map env.delegates
	}
	
	final class Env(val builds: Seq[Build], val tasks: Seq[Task])
	{
		override def toString = "Env:\n  "+ "  Tasks:\n    " + tasks.mkString("\n    ") +"\n" + builds.mkString("\n  ") 
		val buildMap = mapBy(builds)(_.uri)
		val taskMap = mapBy(tasks)(getKey)
		def project(ref: ProjectRef) = buildMap(ref.build).projectMap(ref.project)
		def projectFor(ref: ResolvedReference) = ref match { case pr: ProjectRef => project(pr); case BuildRef(uri) => buildMap(uri).root }
		def allProjects = builds.flatMap(_.allProjects)
		def rootProject(uri: URI): String = buildMap(uri).root.id
		def inheritConfig(ref: ResolvedReference, config: ConfigKey) = projectFor(ref).confMap(config.name).extended map toConfigKey
		def inheritTask(task: AttributeKey[_]) = taskMap(task).delegates map getKey
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
	}
	def getKey: Task => AttributeKey[_] = _.key
	def toConfigKey: Config => ConfigKey = c => ConfigKey(c.name)
	final class Build(val uri: URI, val projects: Seq[Proj])
	{
		override def toString = "Build " + uri.toString + " :\n    " + projects.mkString("\n    ")
		val allProjects = projects map { p => (ProjectRef(uri, p.id), p) }
		val root = projects.head
		val projectMap = mapBy(projects)(_.id)
	}
	final class Proj(val id: String, val delegates: Seq[ProjectRef], val configurations: Seq[Config])
	{
		override def toString = "Project " + id + "\n      Delegates:\n      " + delegates.mkString("\n      ") +
			"\n      Configurations:\n        " + configurations.mkString("\n        ")
		val confMap = mapBy(configurations)(_.name)
	}

	final class Config(val name: String, val extended: Seq[Config])
	{
		override def toString = name + " (extends: " + extended.map(_.name).mkString(", ")  + ")"
	}
	final class Task(val key: AttributeKey[_], val delegates: Seq[Task])
	{
		override def toString = key.label + " (delegates: " + delegates.map(_.key.label).mkString(", ")  + ")"	
	}

	def mapBy[K, T](s: Seq[T])(f: T => K): Map[K, T] = s map { t => (f(t), t) } toMap;

	implicit lazy val arbKeys: Arbitrary[Keys] = Arbitrary(keysGen)
	lazy val keysGen: Gen[Keys] = for(env <- mkEnv; keyCount <- chooseShrinkable(1, KeysPerEnv); keys <- listOfN(keyCount, scope(env)) ) yield new Keys(env, keys)
	
	def scope(env: Env): Gen[Scope] =
		for {
			build <- oneOf(env.builds)
			project <- oneOf(build.projects)
			cAxis <- oneOrGlobal(project.configurations map toConfigKey)
			tAxis <- oneOrGlobal( env.tasks map getKey )
			pAxis <- orGlobal( frequency( (1, BuildRef(build.uri)), (3, ProjectRef(build.uri, project.id) ) ) )
		} yield
			Scope( pAxis, cAxis, tAxis, Global)

	def orGlobal[T](gen: Gen[T]): Gen[ScopeAxis[T]] =
		frequency( (1, gen map Select.apply), (1, Global) )
	def oneOrGlobal[T](gen: Seq[T]): Gen[ScopeAxis[T]] = orGlobal(oneOf(gen))

	implicit lazy val mkEnv: Gen[Env] =
	{
		implicit val cGen = genConfigs(idGen, MaxDepsGen, MaxConfigsGen)
		implicit val tGen = genTasks(idGen, MaxDepsGen, MaxTasksGen)
		implicit val pGen = (uri: URI) => genProjects(uri)(idGen, MaxDepsGen, MaxProjectsGen, cGen)
		envGen(buildGen(uriGen, pGen), tGen)
	}

	implicit lazy val idGen: Gen[String] = for(size <- chooseShrinkable(1, MaxIDSize); cs <- listOfN(size, alphaChar)) yield cs.mkString
	implicit lazy val optIDGen: Gen[Option[String]] = frequency( (1, idGen map some.fn), (1, None) )
	implicit lazy val uriGen: Gen[URI] = for(sch <- idGen; ssp <- idGen; frag <- optIDGen) yield new URI(sch, ssp, frag.orNull)

	implicit def envGen(implicit bGen: Gen[Build], tasks: Gen[Seq[Task]]): Gen[Env] =
		for(i <- MaxBuildsGen; bs <- listOfN(i, bGen); ts <- tasks) yield new Env(bs, ts)
	implicit def buildGen(implicit uGen: Gen[URI], pGen: URI => Gen[Seq[Proj]]): Gen[Build] = for(u <- uGen; ps <- pGen(u)) yield new Build(u, ps)
	
	def nGen[T](igen: Gen[Int])(implicit g: Gen[T]): Gen[List[T]] = igen flatMap { ig => listOfN(ig, g) }

	implicit def genProjects(build: URI)(implicit genID: Gen[String], maxDeps: Gen[Int], count: Gen[Int], confs: Gen[Seq[Config]]): Gen[Seq[Proj]] =
		genAcyclic(maxDeps, genID, count) { (id: String) =>
			for(cs <- confs) yield { (deps: Seq[Proj]) =>
				new Proj(id, deps.map{dep => ProjectRef(build, dep.id) }, cs)
			}
		}
	def genConfigs(implicit genName: Gen[String], maxDeps: Gen[Int], count: Gen[Int]): Gen[Seq[Config]] =
		genAcyclicDirect[Config,String](maxDeps, genName, count)( (key, deps) => new Config(key, deps) )
	def genTasks(implicit genName: Gen[String], maxDeps: Gen[Int], count: Gen[Int]): Gen[Seq[Task]] =
		genAcyclicDirect[Task,String](maxDeps, genName, count)( (key, deps) => new Task(AttributeKey[String](key), deps) )

	def genAcyclicDirect[A,T](maxDeps: Gen[Int], keyGen: Gen[T], max: Gen[Int])(make: (T, Seq[A]) => A): Gen[Seq[ A ]] =
		genAcyclic[A,T](maxDeps, keyGen, max) { t =>
			Gen.value { deps =>
				make(t, deps)
			}
		}

	def genAcyclic[A,T](maxDeps: Gen[Int], keyGen: Gen[T], max: Gen[Int])(make: T => Gen[Seq[A] => A]): Gen[Seq[ A ]] =
		max flatMap { count =>
			listOfN(count, keyGen) flatMap { keys =>
				genAcyclic(maxDeps, keys.distinct)(make)
			}
		}
	def genAcyclic[A,T](maxDeps: Gen[Int], keys: List[T])(make: T => Gen[Seq[A] => A]): Gen[Seq[ A ]] =
		genAcyclic(maxDeps, keys, Nil) flatMap { pairs =>
			sequence( pairs.map { case (key, deps) => mapMake(key, deps, make)  } ) flatMap { inputs =>
				val made = new collection.mutable.HashMap[T, A]
				for( (key, deps, mk) <- inputs)
					made(key) = mk(deps map made)
				keys map made
			}
		}

	def mapMake[A,T](key: T, deps: Seq[T], make: T => Gen[Seq[A] => A]): Gen[Inputs[A,T]] =
		make(key) map { (mk: Seq[A] => A) => (key, deps, mk) }

	def genAcyclic[T](maxDeps: Gen[Int], names: List[T], acc: List[Gen[ (T,Seq[T]) ]]): Gen[Seq[ (T,Seq[T]) ]] =
		names match
		{
			case Nil => sequence(acc)
			case x :: xs =>
				val next = for(depCount <- maxDeps; d <- pick(depCount min xs.size, xs) ) yield (x, d.toList)
				genAcyclic(maxDeps, xs, next :: acc)
		}
	def sequence[T](gs: Seq[Gen[T]]): Gen[Seq[T]] = Gen { prms =>
 		Some(gs map { g => g(prms) getOrElse error("failed generator") })
	}
	type Inputs[A,T] = (T, Seq[T], Seq[A] => A)
}
