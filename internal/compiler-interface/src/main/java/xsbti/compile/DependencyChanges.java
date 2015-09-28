package xsbti.compile;

	import java.io.File;

// only includes changes to dependencies outside of the project
public interface DependencyChanges
{
	boolean isEmpty();
	// class files or jar files
	File[] modifiedBinaries();
	// class names
	String[] modifiedClasses();
}