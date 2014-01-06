package xsbti;

/**
 * The main entry interface for launching applications.   Classes which implement this interface
 * can be launched via the sbt launcher.
 * 
 * In addition, classes can be adapted into this interface by the launcher if they have a static method
 * matching one of these signatures:
 * 
 * - public static void main(String[] args)
 * - public static int main(String[] args)
 * - public static xsbti.Exit main(String[] args)
 *
 */
public interface AppMain
{
	/** Run the application and return the result.
	 * 
	 * @param configuration  The configuration used to run the application.  Includes arguments and access to launcher features.
	 * @return
	 *        The result of running this app.  
	 *        Note: the result can be things like "Please reboot this application". 
	 */
	public MainResult run(AppConfiguration configuration);
}