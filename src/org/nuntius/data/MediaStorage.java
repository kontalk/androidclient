package org.nuntius.data;

import java.io.*;

import android.os.Environment;


public abstract class MediaStorage {

    public static void writeMedia(String filename, String contents) throws IOException {
        File root = Environment.getExternalStorageDirectory();
        root = new File(root, "Nuntius");
        root.mkdirs();
        File f = new File(root, filename);
        FileOutputStream fout = new FileOutputStream(f);
        fout.write(contents.getBytes());
        fout.close();
    }

    public static InputStream readMedia(String filename) throws IOException {
        File root = Environment.getExternalStorageDirectory();
        File f = new File(root.getPath() + File.separator + "Nuntius", filename);
        return new FileInputStream(f);
    }
}
