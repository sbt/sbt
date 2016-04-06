package sbt
package compiler

/**
 * Base trait for the different means of retrieving the compiler bridge sources
 */
sealed trait CompilerBridgeProvider

/**
 * Indicates that the compiler bridge should be retrieved using Ivy.
 * @param ivyConfiguration The `sbt.IvyConfiguration` to use to retrieve the sources.
 * @param module           The module that contains the sources of the compiler bridge.
 */
final case class IvyBridgeProvider(ivyConfiguration: IvyConfiguration, module: ModuleID) extends CompilerBridgeProvider

/**
 * Indicates that the compiler bridge sould be retrieved from the resources on classpath.
 * @param sourceJarName  The name of the JAR containing the bridge sources, to find in the resources.
 * @param reflectJarName The name of the JAR corresponding to `scala-reflect.jar` in the standard scala distribution.
 */
final case class ResourceBridgeProvider(sourceJarName: String, reflectJarName: String) extends CompilerBridgeProvider