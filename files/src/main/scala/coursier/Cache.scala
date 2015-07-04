package coursier

import java.io.{PrintWriter, File}

import coursier.core.MavenRepository

import scala.io.Source

object Cache {

  def mavenRepository(lines: Seq[String]): Option[MavenRepository] = {
    def isMaven =
      lines
        .find(_.startsWith("maven:"))
        .map(_.stripPrefix("maven:").trim)
        .contains("true")

    def ivyLike =
      lines
        .find(_.startsWith("ivy-like:"))
        .map(_.stripPrefix("ivy-like:").trim)
        .contains("true")

    def base =
      lines
        .find(_.startsWith("base:"))
        .map(_.stripPrefix("base:").trim)
        .filter(_.nonEmpty)

    if (isMaven)
      base.map(MavenRepository(_, ivyLike = ivyLike))
    else
      None
  }

  lazy val default = Cache(new File(sys.props("user.home") + "/.coursier/cache"))

}

case class Cache(cache: File) {

  import Cache._

  lazy val repoDir = new File(cache, "repositories")
  lazy val metadataBase = new File(cache, "metadata")
  lazy val fileBase = new File(cache, "files")

  lazy val defaultFile = new File(repoDir, ".default")

  def add(id: String, base: String, ivyLike: Boolean): Unit = {
    repoDir.mkdirs()
    val f = new File(repoDir, id)
    val w = new PrintWriter(f)
    try w.println((Seq("maven: true", s"base: $base") ++ (if (ivyLike) Seq("ivy-like: true") else Nil)).mkString("\n"))
    finally w.close()
  }

  def addCentral(): Unit =
    add("central", "https://repo1.maven.org/maven2/", ivyLike = false)

  def addIvy2Local(): Unit =
    add("ivy2local", "file://" + sys.props("user.home") + "/.ivy2/local/", ivyLike = true)

  def init(ifEmpty: Boolean = true): Unit =
    if (!ifEmpty || !cache.exists()) {
      repoDir.mkdirs()
      metadataBase.mkdirs()
      fileBase.mkdirs()
      addCentral()
      addIvy2Local()
      setDefault("ivy2local", "central")
    }

  def setDefault(ids: String*): Unit = {
    defaultFile.getParentFile.mkdirs()
    val w = new PrintWriter(defaultFile)
    try w.println(ids.mkString("\n"))
    finally w.close()
  }

  def list(): Seq[(String, MavenRepository, (String, File))] =
    Option(repoDir.listFiles())
      .map(_.toSeq)
      .getOrElse(Nil)
      .filter(f => f.isFile && !f.getName.startsWith("."))
      .flatMap { f =>
        val name = f.getName
        val lines = Source.fromFile(f).getLines().toList
        mavenRepository(lines)
          .map(repo =>
            (name, repo.copy(cache = Some(new File(metadataBase, name))), (repo.root, new File(fileBase, name)))
          )
      }

  def map(): Map[String, (MavenRepository, (String, File))] =
    list()
      .map{case (id, repo, fileCache) => id -> (repo, fileCache) }
      .toMap


  def repositories(): Seq[MavenRepository] =
    list().map(_._2)

  def repositoryMap(): Map[String, MavenRepository] =
    list()
      .map{case (id, repo, _) => id -> repo}
      .toMap

  def fileCaches(): Seq[(String, File)] =
    list().map(_._3)

  def default(withNotFound: Boolean = false): Seq[String] =
    if (defaultFile.exists()) {
      val default0 =
        Source.fromFile(defaultFile)
          .getLines()
          .map(_.trim)
          .filter(_.nonEmpty)
          .toList

      val found = list()
        .map(_._1)
        .toSet

      default0
        .filter(found)
    } else
      Nil

  def files(): Files = {
    val map0 = map()
    val default0 = default()

    new Files(default0.map(map0(_)._2), () => ???)
  }

}
