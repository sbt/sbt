package xsbti.compile;

import xsbti.Logger;
import java.io.File;

/*
* This API is subject to change.
*
* It is the client's responsibility to:
*  1. Manage class loaders.  Usually the client will want to:
*    i. Keep the class loader used by the ScalaInstance warm.
*    ii. Keep the class loader of the incremental recompilation classes (xsbti.compile) warm.
*    iii. Share the class loader for Scala classes between the incremental compiler implementation and the ScalaInstance where possible (must be binary compatible)
*  2. Manage the compiler interface jar.  The interface must be compiled against the exact Scala version used for compilation and a compatible Java version.
*  3. Manage compilation order between different compilations.
*    i. Execute a compilation for each dependency, obtaining an Analysis for each.
*    ii. Provide the Analysis from previous compilations to dependent compilations in the analysis map.
*  4. Provide an implementation of JavaCompiler for compiling Java sources.
*  5. Define a function that determines if a classpath entry contains a class (Setup.definesClass).
*    i. This is provided by the client so that the client can cache this information across compilations when compiling multiple sets of sources.
*    ii. The cache should be cleared for each new compilation run or else recompilation will not properly account for changes to the classpath.
*  6. Provide a cache directory.
*    i. This directory is used by IncrementalCompiler to persist data between compilations.
*    ii. It should be a different directory for each set of sources being compiled.
*  7. Manage parallel execution.
*    i. Each compilation may be performed in a different thread as long as the dependencies have been compiled already.
*    ii. Implementations of all types should be immutable and arrays treated as immutable.
*  8. Ensure general invariants:
*    i. The implementations of all types are immutable, except for the already discussed Setup.definesClass.
*    ii. Arrays are treated as immutable.
*    iii. No value is ever null.
*/
public interface IncrementalCompiler
{
	/**
	* Performs an incremental compilation as configured by `in`.
	* The returned Analysis should be provided to compilations depending on the classes from this compilation.
	*/
	CompileResult compile(Inputs in, Logger log);

  /**
   * Creates a compiler instance that can be used by the `compile` method.
   *
   * @param instance The Scala version to use
   * @param interfaceJar The compiler interface jar compiled for the Scala version being used
   * @param options Configures how arguments to the underlying Scala compiler will be built.
   */
  // ScalaCompiler newScalaCompiler(ScalaInstance instance, File interfaceJar, ClasspathOptions options);

 /**
	* Compiles the source interface for a Scala version.  The resulting jar can then be used by the `newScalaCompiler` method
	* to create a ScalaCompiler for incremental compilation.  It is the client's responsibility to manage compiled jars for
	* different Scala versions.
	*
	* @param label A brief name describing the source component for use in error messages
	* @param sourceJar The jar file containing the compiler interface sources.  These are published as sbt's compiler-interface-src module.
	* @param targetJar Where to create the output jar file containing the compiled classes.
	* @param instance The ScalaInstance to compile the compiler interface for.
	* @param log The logger to use during compilation. */
	// void compileInterfaceJar(String label, File sourceJar, File targetJar, File interfaceJar, ScalaInstance instance, Logger log);
}
