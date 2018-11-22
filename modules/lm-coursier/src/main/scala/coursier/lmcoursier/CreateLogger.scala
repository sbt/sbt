package coursier.lmcoursier

final case class CreateLogger(create: () => coursier.Cache.Logger)
