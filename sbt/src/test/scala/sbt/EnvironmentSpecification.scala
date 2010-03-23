/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import org.scalacheck._

object EnvironmentSpecification extends Properties("Environment")
{ s =>
	private[this] type Env = BasicEnvironment { def x: Property[Int] }
	
	val log = new ConsoleLogger

	specify("Non-optional user property assignment", testAssign _)
	specify("Optional user property assignment", testDefaultAssign _)
	specify("Optional user property default and assignment", testDefault _)
	specify("Optional user property default and then assignment", testDefaultThenAssign _)
	specify("Uninitialized empty when all properties are initialized", testUninitializedEmpty _)
	specify("Uninitialized empty when all properties have defaults", testDefaultUninitializedEmpty _)
	specify("Property defaulting to another property ok", propertyDefaultsToProperty _)
	specify("Project-style name+organization", (name: String) => projectEmulation(name.trim))
	
	private def projectEmulation(testName: String) =
	{
		import Prop._
		(!testName.isEmpty) ==>
		withBacking { backing =>
			def env = new DefaultEnv(backing) {
				final def name: String = projectName.value
				final val projectName = propertyLocalF[String](NonEmptyStringFormat)
				final val projectOrganization = propertyOptional[String](name, true)
			}
			val first = env
			first.projectName() = testName
			first.saveEnvironment
			val second = env
			env.projectOrganization.value == testName
		}
	}
	private def propertyDefaultsToProperty(value: Int) =
	{
		withBacking { backing =>
			val env = new DefaultEnv(backing) {
				val base = propertyOptional[Int](value)
				val chained = propertyOptional[Int](base.value)
			}
			env.chained.value == value
		}
	}
	private def testAssign(value: Int) =
	{
		withEnvironment { env =>
			env.x() = value
			env.x.value == value
		}
	}
	private def testDefaultAssign(value: Int, default: Int) =
	{
		withDefaultEnvironment(default) { env =>
			env.x() = value
			env.x.value == value
		}
	}
	private def testDefault(value: Int, default: Int) =
	{
		withDefaultEnvironment(default) { env =>
			env.x.value == default
		}
	}
	private def testDefaultThenAssign(value: Int, default: Int) =
	{
		withDefaultEnvironment(default) { env =>
			env.x.value == default &&
			{
				env.x() = value
				env.x.value == value
			}
		}
	}
	private def testUninitializedEmpty(value: Int) =
	{
		withEnvironment { env =>
			env.x() = value
			env.uninitializedProperties.isEmpty
		}
	}
	private def testDefaultUninitializedEmpty(default: Int) = withDefaultEnvironment(default)(_.uninitializedProperties.isEmpty)
	
	private def defaultEnvironment(default: Int)(backing: Path) = new DefaultEnv(backing) { val x = propertyOptional[Int](default) }
	private def environment(backing: Path) = new DefaultEnv(backing) { val x = property[Int] }
	
	private def withBacking[T](f: Path => T): T = Control.getOrError( FileUtilities.withTemporaryFile(log, "env", "")(file => Right(f(Path.fromFile(file))) ) )
	private def withEnvironment[T](f: Env => T): T = withEnvironmentImpl(environment)(f)
	private def withDefaultEnvironment[T](default: Int)(f: Env => T): T = withEnvironmentImpl(defaultEnvironment(default))(f)
	private def withEnvironmentImpl[T](env: Path => Env)(f: Env => T): T = withBacking(f compose env)

	private class DefaultEnv(val envBackingPath: Path) extends BasicEnvironment { def log = s.log }
}