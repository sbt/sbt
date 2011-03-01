package xsbti;

public final class FullReload extends RuntimeException
{
	private final String[] arguments;
	private final boolean clean;
	public FullReload(String[] arguments)
	{
		this.arguments = arguments;
		this.clean = false;
	}
	public FullReload(String[] arguments, boolean clean)
	{
		this.arguments = arguments;
		this.clean = clean;
	}
	public boolean clean() { return clean; }
	public String[] arguments() { return arguments; }
}