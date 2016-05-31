package coursier

sealed abstract class CachePolicy extends Product with Serializable

object CachePolicy {

  /** Only pick local files, possibly from the cache. Don't try to download anything. */
  case object LocalOnly extends CachePolicy

  /**
    * Only pick local files. If one of these local files corresponds to a changing artifact, check
    * for updates, and download these if needed.
    *
    * If no local file is found, *don't* try download it. Updates are only checked for files already
    * in cache.
    */
  case object LocalUpdateChanging extends CachePolicy

  /**
    * Only pick local files, check if any update is available for them, and download these if needed.
    *
    * If no local file is found, *don't* try download it. Updates are only checked for files already
    * in cache.
    *
    * Unlike `LocalUpdateChanging`, all found local files are checked for updates, not just the
    * changing ones.
    */
  case object LocalUpdate extends CachePolicy

  /**
    * Pick local files, and download the missing ones.
    *
    * For changing ones, check for updates, and download those if any.
    */
  case object UpdateChanging extends CachePolicy

  /**
    * Pick local files, download the missing ones, check for updates and download those if any.
    *
    * Unlike `UpdateChanging`, all found local files are checked for updates, not just the changing
    * ones.
    */
  case object Update extends CachePolicy

  /**
    * Pick local files, download the missing ones.
    *
    * No updates are checked for files already downloaded.
    */
  case object FetchMissing extends CachePolicy

  /**
    * (Re-)download all files.
    *
    * Erases files already in cache.
    */
  case object ForceDownload extends CachePolicy


  private val baseDefault = Seq(
    CachePolicy.LocalOnly,
    CachePolicy.FetchMissing
  )

  def default: Seq[CachePolicy] = {

    def fromOption(value: Option[String], description: String): Option[Seq[CachePolicy]] =
      value.filter(_.nonEmpty).flatMap {
        str =>
          CacheParse.cachePolicies(str) match {
            case scalaz.Success(Seq()) =>
              Console.err.println(
                s"Warning: no mode found in $description, ignoring it."
              )
              None
            case scalaz.Success(policies) =>
              Some(policies)
            case scalaz.Failure(errors) =>
              Console.err.println(
                s"Warning: unrecognized mode in $description, ignoring it."
              )
              None
          }
      }

    val fromEnv = fromOption(
      sys.env.get("COURSIER_MODE"),
      "COURSIER_MODE environment variable"
    )

    def fromProps = fromOption(
      sys.props.get("coursier.mode"),
      "Java property coursier.mode"
    )

    fromEnv
      .orElse(fromProps)
      .getOrElse(baseDefault)
  }

}
