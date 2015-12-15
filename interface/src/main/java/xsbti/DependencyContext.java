package xsbti;

/**
 * Enumeration of existing dependency contexts.
 * Dependency contexts represent the various kind of dependencies that
 * can exist between symbols.
 */
public enum DependencyContext {
	/**
	 * Represents a direct dependency between two symbols :
	 * object Foo
	 * object Bar { def foo = Foo }
	 */
	DependencyByMemberRef,

	/**
	 * Represents an inheritance dependency between two symbols :
	 * class A
	 * class B extends A
	 */
	DependencyByInheritance,

	/**
	 * Dependencies coming from the expansion of a macro
	 */
	DependencyByMacroExpansion
}
