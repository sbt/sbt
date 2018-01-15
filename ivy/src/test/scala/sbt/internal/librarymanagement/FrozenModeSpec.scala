package sbt.internal.librarymanagement

import sbt.librarymanagement._
import sbt.librarymanagement.ivy.UpdateOptions
import sbt.librarymanagement.syntax._

class FrozenModeSpec extends BaseIvySpecification {
  private final val targetDir = Some(currentDependency)
  private final val onlineConf = makeUpdateConfiguration(false, targetDir)
  private final val frozenConf = makeUpdateConfiguration(false, targetDir).withFrozen(true)
  private final val warningConf = UnresolvedWarningConfiguration()
  private final val normalOptions = UpdateOptions()

  final val stoml = Vector("me.vican.jorge" % "stoml_2.12" % "0.4" % "compile")

  /* https://repo1.maven.org/maven2/me/vican/jorge/stoml_2.12/0.4/stoml_2.12-0.4.jar
   * https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar
   * https://repo1.maven.org/maven2/com/lihaoyi/fastparse_2.12/0.4.2/fastparse_2.12-0.4.2.jar
   * https://repo1.maven.org/maven2/com/lihaoyi/fastparse-utils_2.12/0.4.2/fastparse-utils_2.12-0.4.2.jar
   * https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.12/0.1.3/sourcecode_2.12-0.1.3.jar */
  final val explicitStoml = Vector(
    "me.vican.jorge" % "stoml_2.12" % "0.4" % "compile",
    "org.scala-lang" % "scala-library" % "2.12.0" % "compile",
    "com.lihaoyi" % "fastparse_2.12" % "0.4.2" % "compile",
    "com.lihaoyi" % "fastparse-utils_2.12" % "0.4.2" % "compile",
    "com.lihaoyi" % "sourcecode_2.12" % "0.1.3" % "compile"
  )

  it should "fail when artifacts are missing in the cache" in {
    cleanIvyCache()
    def update(module: IvySbt#Module, conf: UpdateConfiguration) =
      IvyActions.updateEither(module, conf, warningConf, log)

    val toResolve = module(defaultModuleId, stoml, None, normalOptions)
    val onlineResolution = update(toResolve, onlineConf)
    assert(onlineResolution.isRight)
    val numberResolved = onlineResolution.right.get.allModules.size

    cleanIvyCache()
    val singleFrozenResolution = update(toResolve, frozenConf)
    assert(singleFrozenResolution.isRight)
    assert(
      singleFrozenResolution.right.get.allModules.size == 1,
      s"The number of explicit modules in frozen mode should 1"
    )

    cleanIvyCache()
    // This relies on the fact that stoml has 5 transitive dependencies
    val toExplicitResolve = module(defaultModuleId, explicitStoml, None, normalOptions)
    val frozenResolution = update(toExplicitResolve, frozenConf)
    assert(frozenResolution.isRight)
    assert(frozenResolution.right.get.allModules.size == numberResolved,
           s"The number of explicit modules in frozen mode should be equal than $numberResolved")
  }
}
