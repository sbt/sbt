/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import sbt.internal.inc.ZincComponentCompiler;
import sbt.internal.inc.ZincComponentManager;
import sbt.librarymanagement.DependencyResolution;
import sbt.librarymanagement.Resolver;
import scala.None$;
import xsbti.ComponentProvider;
import xsbti.GlobalLock;
import xsbti.Logger;

import java.io.File;

public interface ZincBridgeProvider {
    /**
     * Returns an ivy resolver to resolve dependencies locally in the default `.ivy2/local`.
     * <p>
     * For those users interested in using Internet resolvers like Maven Central, you can
     * instantiate them via {@link Resolver#mavenCentral()} et al.
     *
     * @return A local ivy resolver.
     */
    public static Resolver getLocalResolver() {
        return ZincComponentCompiler.LocalResolver();
    }

    /**
     * Returns a global lock that does nothing but calling the callable to synchronize
     * across threads. The lock file is used to resolve and download dependencies via ivy.
     * <p>
     * This operation is necesary to invoke {@link ZincBridgeProvider#getProvider(File, GlobalLock, ComponentProvider, IvyConfiguration, Logger)}.
     *
     * @return A default global lock.
     */
    public static GlobalLock getDefaultLock() {
        return ZincComponentCompiler.getDefaultLock();
    }

    /**
     * Returns a default component provider that retrieves and installs component managers
     * (like the compiled bridge sources) under a given target directory.
     * <p>
     * This is the most simplistic implementation of a component provider. If you need more
     * advanced feature, like management of component via proxies (for companies) or access to
     * other servers, you need to implement your own component provider.
     *
     * @param componentsRoot The directory in which components will be installed and retrieved.
     * @return A default component provider.
     */
    public static ComponentProvider getDefaultComponentProvider(File componentsRoot) {
        return ZincComponentCompiler.getDefaultComponentProvider(componentsRoot);
    }

    /**
     * Get a compiler bridge provider that allows the user to fetch Scala and a compiled bridge.
     *
     * @param scalaJarsTarget   The place where the downloaded Scala jars should be placed.
     * @param lock              The lock file used internally by Ivy to synchronize the dependency resolution.
     * @param componentProvider A provider capable of retrieving existing components or installing
     *                          new ones. The component provider manages compiled bridge sources.
     * @param dependencyResolution The library management module to use to retrieve the bridge.
     * @param logger            The logger.
     * @return A compiler bridge provider capable of fetching scala jars and the compiler bridge.
     */
    public static CompilerBridgeProvider getProvider(File scalaJarsTarget,
                                                     GlobalLock lock,
                                                     ComponentProvider componentProvider,
                                                     DependencyResolution dependencyResolution,
                                                     Logger logger) {
        ZincComponentManager manager = new ZincComponentManager(lock, componentProvider, None$.empty(), logger);
        return ZincComponentCompiler.interfaceProvider(manager, dependencyResolution, scalaJarsTarget);
    }
}
