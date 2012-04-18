package xsbti.compile;

import java.io.File;

/**
* Determines if an entry on a classpath contains a class.
*/
public interface DefinesClass
{
 	/**
	* Returns true if the classpath entry contains the requested class.
	*/
	boolean apply(String className);
}
