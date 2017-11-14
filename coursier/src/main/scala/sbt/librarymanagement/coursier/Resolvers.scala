package sbt.librarymanagement.coursier

import sbt.librarymanagement.{ MavenRepository, Resolver, URLRepository }

object Resolvers {

  private val slowReposBase = Seq(
    "https://repo.typesafe.com/",
    "https://repo.scala-sbt.org/",
    "http://repo.typesafe.com/",
    "http://repo.scala-sbt.org/"
  )

  private val fastReposBase = Seq(
    "http://repo1.maven.org/",
    "https://repo1.maven.org/"
  )

  private def url(res: Resolver): Option[String] =
    res match {
      case m: MavenRepository =>
        Some(m.root)
      case u: URLRepository =>
        u.patterns.artifactPatterns.headOption
          .orElse(u.patterns.ivyPatterns.headOption)
      case _ =>
        None
    }

  private def filterResolvers(bases: Seq[String],
                              resolvers: Seq[(Resolver, Option[String])]): Seq[Resolver] =
    resolvers
      .filter(tuple => tuple._2.exists(url => bases.exists(base => url.startsWith(base))))
      .map(_._1)

  def reorder(resolvers: Seq[Resolver]): Seq[Resolver] = {

    val byUrl = resolvers.map(r => (r, url(r)))

    val fast = filterResolvers(fastReposBase, byUrl)
    val slow = filterResolvers(slowReposBase, byUrl)
    val rest = resolvers.diff(fast).diff(slow)

    val reordered = fast ++ rest ++ slow
    assert(reordered.size == resolvers.size,
           "Reordered resolvers should be the same size as the unordered ones.")

    reordered
  }
}
