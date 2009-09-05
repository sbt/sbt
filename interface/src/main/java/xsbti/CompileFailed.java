package xsbti;

public abstract class CompileFailed extends RuntimeException
{
	public abstract String[] arguments();
}