package lmcoursier.definitions

import dataclass.data

@data class Project(
  module: Module,
  version: String,
  dependencies: Seq[(Configuration, Dependency)],
  configurations: Map[Configuration, Seq[Configuration]],
  properties: Seq[(String, String)],
  packagingOpt: Option[Type],
  publications: Seq[(Configuration, Publication)],
  info: Info
)
