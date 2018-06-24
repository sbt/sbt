package sbtprojectmatrix

import sbt._
import java.util.concurrent.atomic.AtomicBoolean
import scala.language.experimental.macros

object ProjectMatrixPlugin extends AutoPlugin {
  override val requires = sbt.plugins.CorePlugin
  override val trigger = allRequirements
  object autoImport {
    def projectMatrix: ProjectMatrix = macro ProjectMatrix.projectMatrixMacroImpl

   implicit def matrixClasspathDependency[T](
      m: T
   )(implicit ev: T => ProjectMatrixReference): ProjectMatrix.MatrixClasspathDependency =
     ProjectMatrix.MatrixClasspathDependency(m, None)
  }
}
