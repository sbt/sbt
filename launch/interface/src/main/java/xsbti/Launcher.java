package xsbti;

import java.io.File;

public interface Launcher extends ScalaProvider, SbtProvider
{
	public static final int InterfaceVersion = 1;

	public void boot(String[] args);
	public MainResult checkAndLoad(String[] args);
	public MainResult load(String[] args);
	public MainResult load(String[] args, String useSbtVersion, String mainClassName, String definitionScalaVersion);
	public ClassLoader update(String scalaVersion, String sbtVersion);
	public MainResult run(ClassLoader sbtLoader, String mainClassName, SbtConfiguration configuration);

	public File BaseDirectory();
	public File ProjectDirectory();
	public File BootDirectory();
	public File PropertiesFile();

	public Launcher launcher(File base, String mainClassName);
}