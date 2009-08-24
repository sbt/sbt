package xsbti;

import java.io.File;

public interface ScalaProvider
{
	public ClassLoader getScalaLoader(String scalaVersion);
	public File getScalaHome(String scalaVersion);
}