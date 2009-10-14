package xsbti;

import java.io.File;
import java.util.concurrent.Callable;

public interface GlobalLock
{
	public <T> T apply(File lockFile, Callable<T> run);
}