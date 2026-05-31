package lmcoursier

import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import sbt.librarymanagement.*
import sbt.util.Logger

class CoursierDependencyResolutionTests extends AnyPropSpec with Matchers {

  private val logger: Logger = new Logger {
    def log(level: sbt.util.Level.Value, message: => String): Unit =
      System.err.println(s"${level.id} $message")
    def success(message: => String): Unit =
      System.err.println(message)
    def trace(t: => Throwable): Unit =
      System.err.println(s"trace $t")
  }

  private val conf211 =
    CoursierConfiguration().withAutoScalaLibrary(true).withScalaVersion(Some("2.11.12"))
  private val scalaModule212 = ModuleID("org.scala-lang", "scala-library", "2.12.21")
  private val scalaModuleInfo213 = ScalaModuleInfo(
    "2.13.18",
    "2.13",
    Vector.empty,
    checkExplicit = false,
    filterImplicit = false,
    overrideScalaVersion = false
  )

  property("missingOk from passed UpdateConfiguration") {

    val depRes = CoursierDependencyResolution(CoursierConfiguration().withAutoScalaLibrary(false))

    val desc = ModuleDescriptorConfiguration(ModuleID("test", "foo", "1.0"), ModuleInfo("foo"))
      .withDependencies(
        Vector(
          ModuleID("io.get-coursier", "coursier_2.13", "0.1.53")
            .withConfigurations(Some("compile")),
          ModuleID("org.scala-lang", "scala-library", "2.12.11").withConfigurations(Some("compile"))
        )
      )
      .withConfigurations(Vector(Configuration.of("Compile", "compile")))
    val module = depRes.moduleDescriptor(desc)

    depRes
      .update(module, UpdateConfiguration(), UnresolvedWarningConfiguration(), logger)
      .fold(w => (), rep => sys.error(s"Expected resolution to fail, got report $rep"))

    val report = depRes
      .update(
        module,
        UpdateConfiguration().withMissingOk(true),
        UnresolvedWarningConfiguration(),
        logger
      )
      .fold(w => throw w.resolveException, identity)
  }

  property("get scalalib at global version, no scalaModuleInfo") {

    val depRes = CoursierDependencyResolution(conf211)

    val desc = ModuleDescriptorConfiguration(ModuleID("test", "foo", "1.0"), ModuleInfo("foo"))
      .withDependencies(Vector(scalaModule212.withConfigurations(Some("compile"))))
      .withConfigurations(Vector(Configuration.of("Compile", "compile")))
    val module = depRes.moduleDescriptor(desc)

    depRes.update(module, UpdateConfiguration(), UnresolvedWarningConfiguration(), logger) match {
      case Left(x)  => throw x.resolveException
      case Right(x) =>
        x.allModules.collect {
          case m: ModuleID if m.organization == scalaModule212.organization && m.name == m.name =>
            m.revision
        } should (contain(conf211.scalaVersion.get) and have length 1) // from config
    }
  }

  property("get scalalib at local version, scalaModuleInfo:overrideScalaVersion") {

    val depRes = CoursierDependencyResolution(conf211)

    val desc = ModuleDescriptorConfiguration(ModuleID("test", "foo", "1.0"), ModuleInfo("foo"))
      .withDependencies(Vector(scalaModule212.withConfigurations(Some("compile"))))
      .withConfigurations(Vector(Configuration.of("Compile", "compile")))
      .withScalaModuleInfo(scalaModuleInfo213.withOverrideScalaVersion(true))
    val module = depRes.moduleDescriptor(desc)

    depRes.update(module, UpdateConfiguration(), UnresolvedWarningConfiguration(), logger) match {
      case Left(x)  => throw x.resolveException
      case Right(x) =>
        x.allModules.collect {
          case m: ModuleID if m.organization == scalaModule212.organization && m.name == m.name =>
            m.revision
        } should (
          contain(scalaModuleInfo213.scalaFullVersion) and have length 1
        ) // from autoScalaLib
    }
  }

  property("get scalalib at local version, scalaModuleInfo:!overrideScalaVersion") {

    val depRes = CoursierDependencyResolution(conf211)
    val desc = ModuleDescriptorConfiguration(ModuleID("test", "foo", "1.0"), ModuleInfo("foo"))
      .withDependencies(Vector(scalaModule212.withConfigurations(Some("compile"))))
      .withConfigurations(Vector(Configuration.of("Compile", "compile")))
      .withScalaModuleInfo(scalaModuleInfo213.withOverrideScalaVersion(false))
    val module = depRes.moduleDescriptor(desc)

    depRes.update(module, UpdateConfiguration(), UnresolvedWarningConfiguration(), logger) match {
      case Left(x)  => throw x.resolveException
      case Right(x) =>
        x.allModules.collect {
          case m: ModuleID if m.organization == scalaModule212.organization && m.name == m.name =>
            m.revision
        } should (contain(scalaModule212.revision) and have length 1) // from dependency
    }
  }

  property(
    "get scalalib at local version, scalaModuleInfo:overrideScalaVersion, no explicit scala lib"
  ) {

    val depRes = CoursierDependencyResolution(conf211)
    val coursierModule212 = ModuleID("io.get-coursier", "coursier_2.12", "2.1.24")
    val desc = ModuleDescriptorConfiguration(ModuleID("test", "foo", "1.0"), ModuleInfo("foo"))
      .withDependencies(Vector(coursierModule212.withConfigurations(Some("compile"))))
      .withConfigurations(Vector(Configuration.of("Compile", "compile")))
      .withScalaModuleInfo(scalaModuleInfo213.withOverrideScalaVersion(true))
    val module = depRes.moduleDescriptor(desc)

    depRes.update(module, UpdateConfiguration(), UnresolvedWarningConfiguration(), logger) match {
      case Left(x)  => throw x.resolveException
      case Right(x) =>
        x.allModules.collect {
          case m: ModuleID if m.organization == scalaModule212.organization && m.name == m.name =>
            m.revision
        } should (
          contain(scalaModuleInfo213.scalaFullVersion) and have length 1
        ) // from autoScalaLib
    }
  }

  property(
    "get scalalib at local version, scalaModuleInfo:!overrideScalaVersion, no explicit scala lib"
  ) {

    val depRes = CoursierDependencyResolution(conf211)
    val coursierModule212 = ModuleID("io.get-coursier", "coursier_2.12", "2.1.24")
    val desc = ModuleDescriptorConfiguration(ModuleID("test", "foo", "1.0"), ModuleInfo("foo"))
      .withDependencies(Vector(coursierModule212.withConfigurations(Some("compile"))))
      .withConfigurations(Vector(Configuration.of("Compile", "compile")))
      .withScalaModuleInfo(scalaModuleInfo213.withOverrideScalaVersion(false))
    val module = depRes.moduleDescriptor(desc)

    depRes.update(module, UpdateConfiguration(), UnresolvedWarningConfiguration(), logger) match {
      case Left(x)  => throw x.resolveException
      case Right(x) =>
        x.allModules.collect {
          case m: ModuleID if m.organization == scalaModule212.organization && m.name == m.name =>
            CrossVersion.binaryScalaVersion(m.revision)
        } should (contain("2.12") and have length 1) // from transitive dependency
    }
  }

}
