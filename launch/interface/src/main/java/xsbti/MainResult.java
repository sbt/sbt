package xsbti;

/** A launched application should return an instance of this from its 'run' method
* to communicate to the launcher what should be done now that the application
* has competed.  This interface should be treated as 'sealed', with Exit and Reboot the only
* direct subtypes.
*/
public interface MainResult {}