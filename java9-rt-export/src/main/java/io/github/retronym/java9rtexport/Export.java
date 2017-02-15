package io.github.retronym.java9rtexport;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Export {
    public static void main(String[] args) {
        try {

            if (args.length == 0) {
                System.err.println("Usage:");
                System.err.println("    java -jar java9-rt-export-*.jar $HOME/.sbt/java9-rt-ext/rt.jar");
                System.err.println("        Exports rt.jar to the specified path.");
                System.err.println("");
                System.err.println("    java -jar java9-rt-export-*.jar --global-base");
                System.err.println("        Prints sbt global base.");
                System.exit(-1);
            }
            String destination = args[0];
            if (destination.equals("--global-base")) {
                Path defaultGlobalBase = Paths.get(System.getProperty("user.home"), ".sbt", "0.13");
                String globalBase = System.getProperty("sbt.global.base", defaultGlobalBase.toString());
                System.out.println(globalBase);
                System.exit(0);
            }
            FileSystem fileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path path = fileSystem.getPath("/modules");
            Path destPath = Paths.get(destination);
            URI uri = URI.create( "jar:" + destPath.toUri() );
            Map<String, String> env = new HashMap<>();
            env.put( "create", "true" );
            try ( FileSystem zipfs = FileSystems.newFileSystem( uri, env ) ) {
                Iterator<Path> iterator = Files.list(path).iterator();
                while(iterator.hasNext()) {
                    Path next = iterator.next();
                    Copy.copyDirectory(next, zipfs.getPath("/"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
