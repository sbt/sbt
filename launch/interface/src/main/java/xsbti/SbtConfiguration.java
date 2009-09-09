package xsbti;

public interface SbtConfiguration
{
	public String[] arguments();
	public String scalaVersion();
	public String sbtVersion();
	public java.io.File baseDirectory();
	public Launcher launcher();
}