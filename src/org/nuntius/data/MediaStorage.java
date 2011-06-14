package org.nuntius.data;

import java.io.*;

import android.net.Uri;
import android.os.Environment;


public class MediaStorage {

    public static final String URI_SCHEME = "media:";
    public static final File MEDIA_ROOT = new File(Environment.getExternalStorageDirectory(), "Nuntius");

    /* instantiation not allowed */
    private MediaStorage() {}

    public static void writeMedia(String filename, byte[] contents) throws IOException {
        MEDIA_ROOT.mkdirs();
        File f = new File(MEDIA_ROOT, filename);
        FileOutputStream fout = new FileOutputStream(f);
        fout.write(contents);
        fout.close();
    }

    public static InputStream readMedia(String filename) throws IOException {
        if (filename.startsWith(URI_SCHEME))
            filename = filename.substring(URI_SCHEME.length());

        File f = new File(MEDIA_ROOT, filename);
        return new FileInputStream(f);
    }

    public static Uri getMediaUri(String filename) {
        return Uri.fromFile(new File(MEDIA_ROOT, filename));
    }
}
