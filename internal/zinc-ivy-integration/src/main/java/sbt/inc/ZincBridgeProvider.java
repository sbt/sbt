package sbt.inc;

import sbt.internal.inc.ZincComponentCompiler$;
import sbt.internal.inc.ZincComponentManager;
import sbt.internal.librarymanagement.IvyConfiguration;
import sbt.librarymanagement.Resolver;
import sbt.librarymanagement.ResolversSyntax;
import scala.None$;
import xsbti.ComponentProvider;
import xsbti.GlobalLock;
import xsbti.Logger;
import xsbti.compile.CompilerBridgeProvider;

import java.io.File;

public final class ZincBridgeProvider {
    /**
     * Returns an ivy resolver to resolve dependencies locally in the default `.ivy2/local`.
     * <p>
     * For those users interested in using Internet resolvers like Maven Central, you can
     * instantiate them via {@link ResolversSyntax#DefaultMavenRepository()} et al.
     *
     * @return A local ivy resolver.
     */
    public static Resolver getLocalResolver() {
        return ZincComponentCompiler$.MODULE$.LocalResolver();
    }

    /**
     * Get the default ivy configuration to retrieve compiler components.
     * <p>
     * This method is useful to invoke {@link ZincBridgeProvider#getProvider(File, GlobalLock, ComponentProvider, IvyConfiguration, Logger)}.
     * <p>
     * In order to know which arguments to pass, reading the
     * <a href="http://ant.apache.org/ivy/history/latest-milestone/concept.html">ivy main concepts</a>
     * may be useful.
     *
     * @param baseDirectory The base directory for ivy.
     * @param ivyHome       The home for ivy.
     * @param resolvers     The resolvers to be used (usually local and Maven).
     *                      See {@link ZincBridgeProvider#getProvider(File, GlobalLock, ComponentProvider, IvyConfiguration, Logger)}
     *                      and {@link ResolversSyntax}.
     * @return A default ivy configuration ready for fetching Zinc compiler components.
     */
    public static IvyConfiguration getDefaultConfiguration(File baseDirectory, File ivyHome, Resolver[] resolvers, Logger logger) {
        return ZincComponentCompiler$.MODULE$.getDefaultConfiguration(baseDirectory, ivyHome, resolvers, logger);
    }

    /**
     * Get a compiler bridge provider that allows the user to fetch Scala and a compiled bridge.
     *
     * @param scalaJarsTarget   The place where the downloaded Scala jars should be placed.
     * @param lock              The lock file used internally by Ivy to synchronize the dependency resolution.
     * @param componentProvider A provider capable of retrieving existing components or installing
     *                          new ones. The component provider manages compiled bridge sources.
     * @param ivyConfiguration  The ivy configuration used internally by the provider.
     * @param logger            The logger.
     * @return A compiler bridge provider capable of fetching scala jars and the compiler bridge.
     */
    public static CompilerBridgeProvider getProvider(File scalaJarsTarget,
                                                     GlobalLock lock,
                                                     ComponentProvider componentProvider,
                                                     IvyConfiguration ivyConfiguration,
                                                     Logger logger) {
        ZincComponentManager manager = new ZincComponentManager(lock, componentProvider, None$.empty(), logger);
        return ZincComponentCompiler$.MODULE$.interfaceProvider(manager, ivyConfiguration, scalaJarsTarget);
    }
}
