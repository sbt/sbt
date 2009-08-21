package xsbti.boot;

public interface SbtConfiguration
{
	public String[] arguments();
	public String scalaVersion();
	public String sbtVersion();
	public Launcher launcher();
}