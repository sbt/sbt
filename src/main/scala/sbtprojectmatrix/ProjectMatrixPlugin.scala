package sbtprojectmatrix

import sbt._
import internal._
import java.util.concurrent.atomic.AtomicBoolean
import scala.language.experimental.macros

trait ProjectMatrixKeys {
    val projectAxes = settingKey[Seq[VirtualAxis]]("Virtual axes for the project")
}

object ProjectMatrixKeys extends ProjectMatrixKeys

object ProjectMatrixPlugin extends AutoPlugin {
  override val requires = sbt.plugins.CorePlugin
  override val trigger = allRequirements
  object autoImport extends ProjectMatrixKeys {
    def projectMatrix: ProjectMatrix = macro ProjectMatrix.projectMatrixMacroImpl

    implicit def matrixClasspathDependency[T](
      m: T
    )(implicit ev: T => ProjectMatrixReference): ProjectMatrix.MatrixClasspathDependency =
      ProjectMatrix.MatrixClasspathDependency(m, None)

    implicit def matrixReferenceSyntax[T](
      m: T
    )(implicit ev: T => ProjectMatrixReference): ProjectMatrix.ProjectMatrixReferenceSyntax =
      new ProjectMatrix.ProjectMatrixReferenceSyntax(m)
  }
}
