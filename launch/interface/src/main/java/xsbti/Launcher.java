package xsbti;

	import java.io.File;

public interface Launcher
{
	public static final int InterfaceVersion = 1;
	public ScalaProvider getScala(String version);
	public ScalaProvider getScala(String version, String reason);
	public ScalaProvider getScala(String version, String reason, String scalaOrg);
	/**
	 * returns an `AppProvider` which is able to resolve an application
	 * and instantiate its `xsbti.Main` in a new classloader.
	 * See [AppProvider] for more details.
	 * @param id  The artifact coordinates of the application.
	 * @param version The version to resolve
	 */
	public AppProvider app(ApplicationID id, String version);
	/**
	 * This returns the "top" classloader for a launched application.   This classlaoder
	 * lives somewhere *above* that used for the application.   This classloader
	 * is used for doing any sort of JNA/native library loads so that downstream
	 * loaders can share native libraries rather than run into "load-once" restrictions.
	 */
	public ClassLoader topLoader();
	/**
	 * Return the global lock for interacting with the file system.
	 *
	 * A mechanism to do file-based locking correctly on the JVM.  See
	 * the [[GlobalLock]] class for more details.
	 */
	public GlobalLock globalLock();
	/** Value of the `sbt.boot.dir` property, or the default
	 *  boot configuration defined in `boot.directory`.
	 */
	public File bootDirectory();
	/** Configured launcher repositories.  These repositories
	 *  are the same ones used to load the launcher.
	 */
	public xsbti.Repository[] ivyRepositories();
	/** The user has configured the launcher with the only repositories
	 * it wants to use for this applciation.
	 */
	public boolean isOverrideRepositories();
	/**
	 * The value of `ivy.ivy-home` of the boot properties file.
	 * This defaults to the `sbt.ivy.home` property, or `~/.ivy2`.
	 * Use this setting in an application when using Ivy to resolve
	 * more artifacts.
	 *
	 * @returns a file, or null if not set.
	 */
	public File ivyHome();
	/** An array of the checksums that should be checked when retreiving artifacts.
	 *  Configured via the the `ivy.checksums` section of the boot configuration.
	 *  Defaults to sha1, md5 or the value of the `sbt.checksums` property.
	 */
	public String[] checksums();
}
