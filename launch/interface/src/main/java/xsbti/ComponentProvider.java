package xsbti;

import java.io.File;


/**
 * A service to locate, install and modify "Components".
 * 
 * A component is essentially a directory and a set of files attached to a unique string id.
 */
public interface ComponentProvider
{
	/** 
	 * @param id  The component's id string.
	 * @return
	 *       The "working directory" or base directory for the component.  You should perform temporary work here for the component.
	 */
	public File componentLocation(String id);
	/**
	 * Grab the current component definition.
	 * 
	 * @param componentID The component's id string.
	 * @return
	 *     The set of files attached to this component.
	 */
	public File[] component(String componentID);
	/**
	 * This will define a new component using the files passed in.  
	 * 
	 * Note:  The component will copy/move the files into a cache location.  You should not use them directly, but
	 *   look them up using the `component` method.
	 *   
	 * @param componentID  The component's id string
	 * @param components   The set of files which defines the component.
	 * 
	 * @throws BootException if the component is already defined.
	 */
	public void defineComponent(String componentID, File[] components);
	/**
	 * Modify an existing component by adding files to it.
	 * 
	 * @param componentID   The component's id string
	 * @param components    The set of new files to add to the component.
	 * @return              true if any files were copied and false otherwise.
	 *           
	 */
	public boolean addToComponent(String componentID, File[] components);
	/**
	 * @return  The lockfile you should use to ensure your component cache does not become corrupted.
	 *          May return null if there is no lockfile for this provider.
	 */
	public File lockFile();
}