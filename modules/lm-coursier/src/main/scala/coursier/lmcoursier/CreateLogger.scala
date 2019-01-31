package coursier.lmcoursier

import coursier.cache.CacheLogger

final case class CreateLogger(create: () => CacheLogger)
