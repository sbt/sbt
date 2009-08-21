package xsbti.boot;

import java.io.File;

public interface Launcher extends ScalaProvider
{
	public static final int InterfaceVersion = 1;
	
	public void boot(String[] args);
	public MainResult checkAndLoad(String[] args);
	public MainResult load(String[] args);
	public MainResult run(ClassLoader sbtLoader, SbtConfiguration configuration);
	
	public File ProjectDirectory();
	public File BootDirectory();
	public File PropertiesFile();
	
	public Launcher launcher(File base, String mainClassName);
}