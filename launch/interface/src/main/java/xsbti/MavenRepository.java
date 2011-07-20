package xsbti;

	import java.net.URL;

public interface MavenRepository extends Repository
{
	String id();
	URL url();
}