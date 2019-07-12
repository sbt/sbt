package org.pantsbuild.jarjar.util;

// adapted from https://github.com/pantsbuild/jarjar/blob/57845dc73d3e2c9b916ae4a788cfa12114fd7df1/src/main/java/org/pantsbuild/jarjar/util/StandaloneJarProcessor.java
// - made it accepted a List<File> rather than a single File
// - added argument ignoreDuplicateEntries

import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.Enumeration;
import java.io.*;
import java.util.*;

public class CoursierJarProcessor
{
    public static void run(File[] from, File to, JarProcessor proc, boolean ignoreDuplicateEntries) throws IOException {
        byte[] buf = new byte[0x2000];

        final File tmpTo = File.createTempFile("jarjar", ".jar");
        Set<String> entries = new HashSet<>();

        FileOutputStream fos = null;
        JarOutputStream out = null;
        try {
            fos = new FileOutputStream(tmpTo);
            BufferedOutputStream buffered = new BufferedOutputStream(fos);
            out = new JarOutputStream(buffered);

            for (File from0 : from) {
                JarFile in = null;
                try {
                    in = new JarFile(from0);
                    Enumeration<JarEntry> e = in.entries();
                    while (e.hasMoreElements()) {
                        EntryStruct struct = new EntryStruct();
                        JarEntry entry = e.nextElement();
                        struct.name = entry.getName();
                        struct.time = entry.getTime();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        IoUtil.pipe(in.getInputStream(entry), baos, buf);
                        struct.data = baos.toByteArray();
                        if (proc.process(struct)) {
                            if (entries.add(struct.name)) {
                                entry = new JarEntry(struct.name);
                                entry.setTime(struct.time);
                                entry.setCompressedSize(-1);
                                out.putNextEntry(entry);
                                out.write(struct.data);
                            } else if (struct.name.endsWith("/")) {
                                // TODO(chrisn): log
                            } else if (!ignoreDuplicateEntries) {
                                throw new DuplicateJarEntryException(from0.getAbsolutePath(), struct.name);
                            }
                        }
                    }

                } finally {
                    if (in != null)
                        in.close();
                }
            }
        } finally {
            if (out != null)
              out.close();
            if (fos != null)
                fos.close();
        }

         // delete the empty directories
        IoUtil.copyZipWithoutEmptyDirectories(tmpTo, to);
        tmpTo.delete();

    }
}
