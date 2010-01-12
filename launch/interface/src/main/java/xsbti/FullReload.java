package xsbti;

public final class FullReload extends RuntimeException
{
	private final String[] arguments;
	public FullReload(String[] arguments)
	{
		this.arguments = arguments;
	}
	public String[] arguments() { return arguments; }
}