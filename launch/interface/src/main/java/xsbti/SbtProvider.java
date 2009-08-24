package xsbti;

import java.io.File;

public interface SbtProvider
{
	public ClassLoader createSbtLoader(String sbtVersion, String scalaVersion);
	public ClassLoader createSbtLoader(String sbtVersion, String scalaVersion, ClassLoader parentLoader);

	public File getSbtHome(String sbtVersion, String scalaVersion);
	public File componentLocation(String sbtVersion, String id, String scalaVersion);
}