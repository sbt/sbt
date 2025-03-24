package sbt

/** Identifies a project matrix. */
sealed trait ProjectMatrixReference

/** Identifies a project in the current build context. */
final case class LocalProjectMatrix(id: String) extends ProjectMatrixReference
