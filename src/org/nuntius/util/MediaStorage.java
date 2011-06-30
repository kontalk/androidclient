package org.nuntius.util;

import java.io.*;

import android.content.Context;
import android.os.Environment;


public class MediaStorage {
    private static final File MEDIA_ROOT = new File(Environment.getExternalStorageDirectory(), "Nuntius");

    /* instantiation not allowed */
    private MediaStorage() {}

    public static File writeInternalMedia(Context context, String filename, byte[] contents) throws IOException {
        File f = new File(context.getCacheDir(), filename);
        FileOutputStream fout = new FileOutputStream(f);
        fout.write(contents);
        fout.close();
        return f;
    }

    public static File writeMedia(String filename, InputStream source) throws IOException {
        MEDIA_ROOT.mkdirs();
        File f = new File(MEDIA_ROOT, filename);
        FileOutputStream fout = new FileOutputStream(f);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = source.read(buffer)) != -1)
            fout.write(buffer, 0, len);
        fout.close();
        return f;
    }

}
