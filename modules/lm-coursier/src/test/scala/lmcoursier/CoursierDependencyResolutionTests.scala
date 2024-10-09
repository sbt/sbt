package lmcoursier

import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import sbt.librarymanagement.ModuleID
import sbt.librarymanagement.UpdateConfiguration
import sbt.librarymanagement.UnresolvedWarningConfiguration
import sbt.util.Logger
import sbt.librarymanagement.ModuleInfo
import sbt.librarymanagement.ModuleDescriptorConfiguration
import sbt.librarymanagement.Configuration

class CoursierDependencyResolutionTests extends AnyPropSpec with Matchers {

  property("missingOk from passed UpdateConfiguration") {

    val depRes = CoursierDependencyResolution(CoursierConfiguration().withAutoScalaLibrary(false))

    val desc = ModuleDescriptorConfiguration(ModuleID("test", "foo", "1.0"), ModuleInfo("foo"))
      .withDependencies(Vector(
        ModuleID("io.get-coursier", "coursier_2.13", "0.1.53").withConfigurations(Some("compile")),
        ModuleID("org.scala-lang", "scala-library", "2.12.11").withConfigurations(Some("compile"))
      ))
      .withConfigurations(Vector(Configuration.of("Compile", "compile")))
    val module = depRes.moduleDescriptor(desc)

    val logger: Logger = new Logger {
      def log(level: sbt.util.Level.Value, message: => String): Unit =
        System.err.println(s"${level.id} $message")
      def success(message: => String): Unit =
        System.err.println(message)
      def trace(t: => Throwable): Unit =
        System.err.println(s"trace $t")
    }

    depRes.update(module, UpdateConfiguration(), UnresolvedWarningConfiguration(), logger)
      .fold(w => (), rep => sys.error(s"Expected resolution to fail, got report $rep"))

    val report = depRes.update(module, UpdateConfiguration().withMissingOk(true), UnresolvedWarningConfiguration(), logger)
      .fold(w => throw w.resolveException, identity)
  }

}
