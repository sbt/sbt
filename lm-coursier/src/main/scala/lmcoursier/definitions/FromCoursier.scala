package lmcoursier.definitions

// TODO Make private[lmcoursier]
// private[coursier]
object FromCoursier {

  def cachePolicy(r: coursier.cache.CachePolicy): CachePolicy =
    r match {
      case coursier.cache.CachePolicy.LocalOnly => CachePolicy.LocalOnly
      case coursier.cache.CachePolicy.LocalOnlyIfValid => CachePolicy.LocalOnlyIfValid
      case coursier.cache.CachePolicy.LocalUpdateChanging => CachePolicy.LocalUpdateChanging
      case coursier.cache.CachePolicy.LocalUpdate => CachePolicy.LocalUpdate
      case coursier.cache.CachePolicy.UpdateChanging => CachePolicy.UpdateChanging
      case coursier.cache.CachePolicy.Update => CachePolicy.Update
      case coursier.cache.CachePolicy.FetchMissing => CachePolicy.FetchMissing
      case coursier.cache.CachePolicy.ForceDownload => CachePolicy.ForceDownload
    }
}