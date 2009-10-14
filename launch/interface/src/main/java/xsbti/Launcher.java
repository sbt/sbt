package xsbti;


public interface Launcher
{
	public static final int InterfaceVersion = 1;
	public ScalaProvider getScala(String version);
	public ClassLoader topLoader();
	public GlobalLock globalLock();
}