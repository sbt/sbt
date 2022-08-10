package sbt

/**
 * Indicate whether the project was created organically, synthesized by a plugin,
 * or is a "generic root" project supplied by sbt when a project doesn't exist for `file(".")`.
 */
enum ProjectOrigin:
  case Organic
  case ExtraProject
  case DerivedProject
  case GenericRoot
