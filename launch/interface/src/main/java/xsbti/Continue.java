package xsbti;

/** A launched application returns an instance of this class in order to communicate to the launcher
* that the application's main thread is finished and the launcher's work is complete, but it should not exit.*/
public interface Continue extends MainResult {}