package xsbti;

/**
 * A launched application should return an instance of this from its 'run' method
 * to communicate to the launcher what should be done now that the application
 * has completed.  This interface should be treated as 'sealed', with Exit and Reboot the only
 * direct subtypes.
 *
 * @see xsbti.Exit
 * @see xsbti.Reboot
 */
public interface MainResult {}