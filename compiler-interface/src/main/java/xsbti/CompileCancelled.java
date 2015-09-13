package xsbti;

/**
 * An exception thrown when compilation cancellation has been requested during
 * Scala compiler run.
 */
public abstract class CompileCancelled extends RuntimeException {
	public abstract String[] arguments();
}
