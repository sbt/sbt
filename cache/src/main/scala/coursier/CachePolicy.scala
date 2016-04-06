package coursier

sealed abstract class CachePolicy extends Product with Serializable

object CachePolicy {
  case object LocalOnly extends CachePolicy
  case object LocalUpdateChanging extends CachePolicy
  case object LocalUpdate extends CachePolicy
  case object UpdateChanging extends CachePolicy
  case object Update extends CachePolicy
  case object FetchMissing extends CachePolicy
  case object ForceDownload extends CachePolicy
}
