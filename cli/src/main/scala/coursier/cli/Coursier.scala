package coursier
package cli

import java.io.File

import caseapp._
import coursier.core.{CachePolicy, Parse}
import coursier.core.{ArtifactDownloaderLogger, RemoteLogger, ArtifactDownloader}

import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

case class Coursier(scope: List[String],
                    keepOptional: Boolean,
                    fetch: Boolean,
                    @ExtraName("N") maxIterations: Int) extends App {

  val scopes0 =
    if (scope.isEmpty) List(Scope.Compile, Scope.Runtime)
    else scope.map(Parse.scope)
  val scopes = scopes0.toSet

  val centralCacheDir = new File(sys.props("user.home") + "/.coursier/cache/central")

  val base = centralCacheDir.toURI
  def fileRepr(f: File) =
    base.relativize(f.toURI).getPath

  val logger: RemoteLogger with ArtifactDownloaderLogger = new RemoteLogger with ArtifactDownloaderLogger {
    def println(s: String) = Console.err.println(s)

    def downloading(url: String) =
      println(s"Downloading $url")
    def downloaded(url: String, success: Boolean) =
      println(
        if (success) s"Downloaded $url"
        else s"Failed to download $url"
      )
    def readingFromCache(f: File) = {
      println(s"Reading ${fileRepr(f)} from cache")
    }
    def puttingInCache(f: File) =
      println(s"Writing ${fileRepr(f)} in cache")

    def foundLocally(f: File) =
      println(s"Found locally ${fileRepr(f)}")
    def downloadingArtifact(url: String) =
      println(s"Downloading $url")
    def downloadedArtifact(url: String, success: Boolean) =
      println(
        if (success) s"Downloaded $url"
        else s"Failed to download $url"
      )
  }

  val cachedMavenCentral = repository.mavenCentral.copy(cache = Some(centralCacheDir), logger = Some(logger))
  val repositories = Seq[Repository](
    cachedMavenCentral
  )

  lazy val downloaders = Map[Repository, ArtifactDownloader](
    cachedMavenCentral -> ArtifactDownloader(repository.mavenCentral.root, centralCacheDir, logger = Some(logger))
  )

  val (splitArtifacts, malformed) = remainingArgs.toList
    .map(_.split(":", 3).toSeq)
    .partition(_.length == 3)

  if (splitArtifacts.isEmpty) {
    Console.err.println("Usage: coursier artifacts...")
    sys exit 1
  }

  if (malformed.nonEmpty) {
    Console.err.println(s"Malformed artifacts:\n${malformed.map(_.mkString(":")).mkString("\n")}")
    sys exit 1
  }

  val modules = splitArtifacts.map{
    case Seq(org, name, version) =>
      (Module(org, name), version)
  }

  val deps = modules.map{case (mod, ver) =>
    Dependency(mod, ver, scope = Scope.Runtime)
  }

  val res = resolve(
    deps.toSet,
    fetchFrom(repositories),
    maxIterations = Some(maxIterations).filter(_ > 0),
    filter = Some(dep => (keepOptional || !dep.optional) && scopes(dep.scope))
  ).run

  if (!res.isDone) {
    Console.err.println(s"Maximum number of iteration reached!")
    sys exit 1
  }

  def repr(dep: Dependency) =
    s"${dep.module.organization}:${dep.module.name}:${dep.`type`}:${Some(dep.classifier).filter(_.nonEmpty).map(_+":").mkString}${dep.version}"

  val trDeps = res.dependencies.toList.sortBy(repr)

  println("\n" + trDeps.map(repr).distinct.mkString("\n"))

  if (res.conflicts.nonEmpty) {
    // Needs test
    println(s"${res.conflicts.size} conflict(s):\n  ${res.conflicts.toList.map(repr).sorted.mkString("  \n")}")
  }

  val errDeps = trDeps.filter(dep => res.errors.contains(dep.moduleVersion))
  if (errDeps.nonEmpty) {
    println(s"${errDeps.size} error(s):")
    for (dep <- errDeps) {
      println(s"  ${dep.module}:\n    ${res.errors(dep.moduleVersion).mkString("\n").replace("\n", "    \n")}")
    }
  }

  if (fetch) {
    println()

    val cachePolicy: CachePolicy = CachePolicy.Default

    val m = res.dependencies.groupBy(dep => res.projectsCache.get(dep.moduleVersion).map(_._1))
    val (notFound, remaining0) = m.partition(_._1.isEmpty)
    if (notFound.nonEmpty) {
      val notFound0 = notFound.values.flatten.toList.map(repr).sorted
      println(s"Not found:${notFound0.mkString("\n")}")
    }

    val (remaining, downloaderNotFound) = remaining0.partition(t => downloaders.contains(t._1.get))
    if (downloaderNotFound.nonEmpty) {
      val downloaderNotFound0 = downloaderNotFound.values.flatten.toList.map(repr).sorted
      println(s"Don't know how to download:${downloaderNotFound0.mkString("\n")}")
    }

    val sorted = remaining
      .toList
      .map{ case (Some(repo), deps) => repo -> deps.toList.sortBy(repr) }
      .sortBy(_._1.toString) // ...

    val tasks =
      for {
        (repo, deps) <- sorted
        dl = downloaders(repo)
        dep <- deps
      } yield {
        dl.artifact(dep, cachePolicy = cachePolicy).run.map {
          case -\/(err) =>
            println(s"Failed to get ${repr(dep)}: $err")
          case \/-(f) =>
            println(s"${repr(dep)}:\n  ${fileRepr(f)}")
        }
      }

    val task = Task.gatherUnordered(tasks)

    task.run
  }
}

object Coursier extends AppOf[Coursier] {
  val parser = default
}
