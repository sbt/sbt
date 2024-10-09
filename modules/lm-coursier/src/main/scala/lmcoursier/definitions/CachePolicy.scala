package lmcoursier.definitions

sealed abstract class CachePolicy extends Serializable

object CachePolicy {
  /* NOTE: the following comments are copied from coursier.cache.CachePolicy for the benefit of users within an IDE
  that reads the javadocs. Please keep in sync from the original ADT.
   */

  /** Only pick local files, possibly from the cache. Don't try to download anything. */
  case object LocalOnly extends CachePolicy

  /** Only pick local files, possibly from the cache. Don't return changing artifacts (whose last check is) older than TTL */
  case object LocalOnlyIfValid extends CachePolicy

  /**
   * Only pick local files. If one of these local files corresponds to a changing artifact, check
   * for updates, and download these if needed.
   *
   * If no local file is found, *don't* try download it. Updates are only checked for files already
   * in cache.
   *
   * Follows the TTL parameter (assumes no update is needed if the last one is recent enough).
   */
  case object LocalUpdateChanging extends CachePolicy

  /**
   * Only pick local files, check if any update is available for them, and download these if needed.
   *
   * If no local file is found, *don't* try download it. Updates are only checked for files already
   * in cache.
   *
   * Follows the TTL parameter (assumes no update is needed if the last one is recent enough).
   *
   * Unlike `LocalUpdateChanging`, all found local files are checked for updates, not just the
   * changing ones.
   */
  case object LocalUpdate extends CachePolicy

  /**
   * Pick local files, and download the missing ones.
   *
   * For changing ones, check for updates, and download those if any.
   *
   * Follows the TTL parameter (assumes no update is needed if the last one is recent enough).
   */
  case object UpdateChanging extends CachePolicy

  /**
   * Pick local files, download the missing ones, check for updates and download those if any.
   *
   * Follows the TTL parameter (assumes no update is needed if the last one is recent enough).
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
}