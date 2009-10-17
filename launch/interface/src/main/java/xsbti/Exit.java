package xsbti;

/** A launched application returns an instance of this class in order to communicate to the launcher
* that the application is completely finished and the launcher should exit with the given exit code.*/
public interface Exit extends MainResult
{
	public int code();
}