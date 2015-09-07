package sbt.internal.librarymanagement;

	import java.util.Map;
	import org.apache.ivy.plugins.resolver.DependencyResolver;

// implements the methods with raw types
@SuppressWarnings("rawtypes")
public abstract class ResolverAdapter implements DependencyResolver
{
    public String[] listTokenValues(String token, Map otherTokenValues) { return new String[0]; }

    public Map[] listTokenValues(String[] tokens, Map criteria) { return new Map[0]; }
}