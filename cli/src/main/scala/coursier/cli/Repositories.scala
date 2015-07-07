package coursier.cli

import coursier.Cache
import caseapp._

// TODO: allow removing a repository (with confirmations, etc.)
case class Repositories(
  @ValueDescription("id:baseUrl") @ExtraName("a") add: List[String],
  @ExtraName("L") list: Boolean,
  @ExtraName("l") defaultList: Boolean,
  ivyLike: Boolean
) extends App {

  if (add.exists(!_.contains(":"))) {
    CaseApp.printUsage[Repositories](err = true)
    sys.exit(255)
  }

  val add0 = add
    .map{ s =>
      val Seq(id, baseUrl) = s.split(":", 2).toSeq
      id -> baseUrl
    }

  if (
    add0.exists(_._1.contains("/")) ||
    add0.exists(_._1.startsWith(".")) ||
    add0.exists(_._1.isEmpty)
  ) {
    CaseApp.printUsage[Repositories](err = true)
    sys.exit(255)
  }


  val cache = Cache.default

  if (cache.cache.exists() && !cache.cache.isDirectory) {
    Console.err.println(s"Error: ${cache.cache} not a directory")
    sys.exit(1)
  }

  if (!cache.cache.exists())
    cache.init(verbose = true)

  val current = cache.list().map(_._1).toSet

  val alreadyAdded = add0
    .map(_._1)
    .filter(current)

  if (alreadyAdded.nonEmpty) {
    Console.err.println(s"Error: already added: ${alreadyAdded.mkString(", ")}")
    sys.exit(1)
  }

  for ((id, baseUrl0) <- add0) {
    val baseUrl =
      if (baseUrl0.endsWith("/"))
        baseUrl0
      else
        baseUrl0 + "/"

    cache.add(id, baseUrl, ivyLike = ivyLike)
  }

  if (defaultList) {
    val map = cache.repositoryMap()

    for (id <- cache.default(withNotFound = true))
      map.get(id) match {
        case Some(repo) =>
          println(s"$id: ${repo.root}" + (if (repo.ivyLike) " (Ivy-like)" else ""))
        case None =>
          println(s"$id (not found)")
      }
  }

  if (list)
    for ((id, repo, _) <- cache.list().sortBy(_._1)) {
      println(s"$id: ${repo.root}" + (if (repo.ivyLike) " (Ivy-like)" else ""))
    }

}

object Repositories extends AppOf[Repositories] {
  val parser = default
}
