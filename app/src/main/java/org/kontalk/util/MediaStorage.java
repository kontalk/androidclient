/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.RawRes;
import android.support.media.ExifInterface;
import android.support.v4.app.Fragment;
import android.webkit.MimeTypeMap;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.reporting.ReportingManager;


/**
 * Media storage utilities.
 * @author Daniele Ricci
 */
public abstract class MediaStorage {
    private static final String TAG = Kontalk.TAG;

    public static final String UNKNOWN_FILENAME = "unknown_file.bin";

    private static final File DCIM_ROOT = new File(Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        "Kontalk");
    private static final File PICTURES_ROOT = new File(Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "Kontalk");
    private static final File PICTURES_SENT_ROOT = new File(new File(Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "Kontalk"), "Sent");
    private static final File AUDIO_ROOT = new File(Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        "Kontalk");
    private static final File AUDIO_SENT_ROOT = new File(new File(Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        "Kontalk"), "Sent");
    private static final File DOWNLOADS_ROOT = new File(Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Kontalk");

    private static final DateFormat sDateFormat =
        new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US);

    private static final int THUMBNAIL_WIDTH = 512;
    private static final int THUMBNAIL_HEIGHT = 512;
    public static final String THUMBNAIL_MIME = "image/png";
    public static final String THUMBNAIL_MIME_NETWORK = "image/jpeg";
    public static final int THUMBNAIL_MIME_COMPRESSION = 50;

    public static final String COMPRESS_MIME = "image/jpeg";
    private static final int COMPRESSION_QUALITY = 85;

    public static final int OUTGOING_MESSAGE_SOUND = R.raw.sound_outgoing;
    // TODO
    public static final int INCOMING_MESSAGE_SOUND = 0;

    /** Media player used by {@link #playNotificationSound}. */
    private static QuickMediaPlayer mMediaPlayer;

    public static boolean isExternalStorageAvailable() {
        return Environment.getExternalStorageState()
            .equals(Environment.MEDIA_MOUNTED);
    }

    public static File getInternalMediaFile(Context context, String filename) {
        return new File(context.getCacheDir(), filename);
    }

    /** Writes a media to the internal cache. */
    public static File writeInternalMedia(Context context, String filename, byte[] contents) throws IOException {
        File file = getInternalMediaFile(context, filename);
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(contents);
        fout.close();
        return file;
    }

    private static BitmapFactory.Options processOptions(BitmapFactory.Options options,
            int scaleWidth, int scaleHeight) {
        int w = options.outWidth;
        int h = options.outHeight;
        // error :(
        if (w < 0 || h < 0) return null;

        if (w > scaleWidth)
            options.inSampleSize = (w / scaleWidth);
        else if (h > scaleHeight)
            options.inSampleSize = (h / scaleHeight);

        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    /** Generates {@link BitmapFactory.Options} for the given {@link InputStream}. */
    public static BitmapFactory.Options preloadBitmap(InputStream in, int scaleWidth, int scaleHeight) {
        BitmapFactory.Options options = bitmapOptionsDecodeBounds();
        BitmapFactory.decodeStream(in, null, options);
        return processOptions(options, scaleWidth, scaleHeight);
    }

    private static BitmapFactory.Options bitmapOptionsDecodeBounds() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        return options;
    }

    private static BitmapFactory.Options bitmapOptions() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    public static Bitmap loadBitmapSimple(InputStream in) {
        BitmapFactory.Options options = bitmapOptions();
        return BitmapFactory.decodeStream(in, null, options);
    }

    /** Writes a thumbnail of a media to the internal cache. */
    public static File cacheThumbnail(Context context, Uri media, String filename, boolean forNetwork) throws IOException {
        File file = new File(context.getCacheDir(), filename);
        cacheThumbnail(context, media, file, forNetwork);
        return file;
    }

    /** Writes a thumbnail of a media to a {@link File}. */
    public static void cacheThumbnail(Context context, Uri media, File destination, boolean forNetwork) throws IOException {
        FileOutputStream fout = new FileOutputStream(destination);
        try {
            cacheThumbnail(context, media, fout, forNetwork);
        }
        finally {
            fout.close();
        }
    }

    private static void cacheThumbnail(Context context, Uri media, FileOutputStream fout, boolean forNetwork) throws IOException {
        resizeImage(context, media, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                forNetwork ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,
            forNetwork ? THUMBNAIL_MIME_COMPRESSION : 0,
            fout);
    }

    /**
     * Tries various methods for obtaining the rotation of the image.
     * @return a matrix to rotate the image (if any)
     */
    private static Matrix getRotation(Context context, Uri media) throws IOException {
        // method 1: query the media storage
        Cursor cursor = context.getContentResolver().query(media,
            new String[] { MediaStore.Images.ImageColumns.ORIENTATION }, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int orientation = cursor.getInt(0);

                    if (orientation != 0) {
                        Matrix m = new Matrix();
                        m.postRotate(orientation);

                        return m;
                    }
                }
            }
            catch (Exception e) {
                ReportingManager.logException(e);
                // we'll try the next method
            }
            finally {
                cursor.close();
            }
        }

        // method 2: write media contents to a temporary file and run ExifInterface
        InputStream in = context.getContentResolver().openInputStream(media);
        OutputStream out = null;
        File tmp = null;
        try {
            tmp = File.createTempFile("rotation", null, context.getCacheDir());
            out = new FileOutputStream(tmp);

            SystemUtils.copy(in, out);
            // flush the file
            out.close();

            ExifInterface exif = new ExifInterface(tmp.toString());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return null;
            }

            return matrix;
        }
        finally {
            if (tmp != null)
                tmp.delete();
            SystemUtils.closeStream(in);
            SystemUtils.closeStream(out);
        }
    }

    /** Apply a rotation matrix respecting the image orientation. */
    static Bitmap bitmapOrientation(Context context, Uri media, Bitmap bitmap) {
        // check if we have to (and can) rotate the thumbnail
        try {
            Matrix m = getRotation(context, media);
            if (m != null) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            }
        }
        catch (Exception e) {
            Log.w(TAG, "unable to check for rotation data", e);
        }

        return bitmap;
    }

    public static long getLength(Context context, Uri media) throws IOException {
        AssetFileDescriptor stat = null;
        long length = 0;
        try {
            stat = context.getContentResolver().openAssetFileDescriptor(media, "r");
            if (stat != null)
                length = stat.getLength();
        }
        finally {
            try {
                if (stat != null)
                    stat.close();
            }
            catch (IOException e) {
                // ignored
            }
        }

        if (length == 0) {
            // try to count bytes by reading it
            InputStream in = null;
            try {
                in = context.getContentResolver().openInputStream(media);
                CountingInputStream counter = new CountingInputStream(in);
                counter.consume();
                length = counter.getByteCount();
            }
            finally {
                try {
                    if (in != null)
                        in.close();
                }
                catch (IOException e) {
                    // ignored
                }
            }
        }

        return length;
    }

    private static final class CountingInputStream extends InputStream {
        private final InputStream mInputStream;
        private long mBytes;

        public CountingInputStream(InputStream in) {
            mInputStream = in;
        }

        @Override
        public int available() throws IOException {
            return mInputStream.available();
        }

        @Override
        public void close() throws IOException {
            mInputStream.close();
        }

        @Override
        public int read() throws IOException {
            int data = mInputStream.read();
            if (data >= 0)
                mBytes++;
            return data;
        }

        public long getByteCount() {
            return mBytes;
        }

        public void consume() throws IOException {
            while (read() >= 0);
        }
    }

    /** Creates a temporary JPEG file for a photo (DCIM). */
    public static File getOutgoingPhotoFile() throws IOException {
        return getOutgoingPhotoFile(new Date());
    }

    private static File getOutgoingPhotoFile(Date date) throws IOException {
        return createImageFile(DCIM_ROOT, date);
    }

    /** Creates a temporary JPEG file for a picture (Pictures). */
    public static File getOutgoingPictureFile() throws IOException {
        return getOutgoingPictureFile(new Date());
    }

    private static File getOutgoingPictureFile(Date date) throws IOException {
        createNoMedia(PICTURES_SENT_ROOT);
        return createImageFile(PICTURES_SENT_ROOT, date);
    }

    private static File createImageFile(File path, Date date) throws IOException {
        createMedia(path);
        String timeStamp = sDateFormat.format(date);
        File f = new File(path, "IMG_" + timeStamp + ".jpg");
        f.createNewFile();
        return f;
    }

    public static String getOutgoingPictureFilename(Date date, String extension) {
        String timeStamp = sDateFormat.format(date);
        return "IMG_" + timeStamp + "." + extension;
    }

    /** Creates a file object for an incoming image file. */
    public static File getIncomingImageFile(Date date, String extension) {
        createMedia(PICTURES_ROOT);
        String timeStamp = sDateFormat.format(date);
        return new File(PICTURES_ROOT, "IMG_" + timeStamp + "." + extension);
    }

    /** Creates a temporary 3gp file. */
    public static File getOutgoingAudioFile() throws IOException {
        return getOutgoingAudioFile(new Date());
    }

    private static File getOutgoingAudioFile(Date date) throws IOException {
        createNoMedia(AUDIO_SENT_ROOT);
        String timeStamp = sDateFormat.format(date);
        File f = new File(AUDIO_SENT_ROOT, "record_" + timeStamp + ".3gp");
        f.createNewFile();
        return f;
    }

    public static String getOutgoingAudioFilename(Date date, String extension) {
        String timeStamp = sDateFormat.format(date);
        return "audio_" + timeStamp + "." + extension;
    }

    /** Creates a file object for an incoming audio file. */
    public static File getIncomingAudioFile(Date date, String extension) {
        createNoMedia(AUDIO_ROOT);
        String timeStamp = sDateFormat.format(date);
        return new File(AUDIO_ROOT, "audio_" + timeStamp + "." + extension);
    }

    public static File getIncomingFile(Date date, String extension) {
        createMedia(DOWNLOADS_ROOT);
        String timeStamp = sDateFormat.format(date);
        return new File(DOWNLOADS_ROOT, "file_" + timeStamp + "." + extension);
    }

    /** Ensures that the given path exists. */
    private static boolean createMedia(File path) {
        return path.isDirectory() || path.mkdirs();
    }

    /** Ensures that the given path exists and a .nomedia file exists. */
    private static boolean createNoMedia(File path) {
        try {
            if (createMedia(path)) {
                File nomedia = new File(path, ".nomedia");
                return nomedia.isFile() || nomedia.createNewFile();
            }
            return false;
        }
        catch (Exception e) {
            return false;
        }
    }

    /** Guesses the MIME type of an {@link Uri}. */
    public static String getType(Context context, Uri uri) {
        // try Android detection
        String mime = context.getContentResolver().getType(uri);

        // the following methods actually use the same underlying implementation
        // (libcore.net.MimeUtils), but that could change in the future so no
        // hurt in trying them all just in case.
        // Lowercasing the filename seems to help in detecting the correct MIME.

        if (mime == null)
            // try WebKit detection
            mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(MimeTypeMap
                    .getFileExtensionFromUrl(uri.toString()).toLowerCase());
        if (mime == null)
            // try Java detection
            mime = URLConnection.guessContentTypeFromName(uri.toString().toLowerCase());
        return mime;
    }

    /** Guesses the MIME type of an URL. */
    public static String getType(String url) {
        String mime;

        // the following methods actually use the same underlying implementation
        // (libcore.net.MimeUtils), but that could change in the future so no
        // hurt in trying them all just in case.
        // Lowercasing the filename seems to help in detecting the correct MIME.

        // try WebKit detection
        mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(MimeTypeMap
                .getFileExtensionFromUrl(url).toLowerCase());
        if (mime == null)
            // try Java detection
            mime = URLConnection.guessContentTypeFromName(url.toLowerCase());
        return mime;
    }

    public static File resizeImage(Context context, Uri uri, int maxSize) throws IOException {
        return resizeImage(context, uri, maxSize, maxSize, COMPRESSION_QUALITY);
    }

    public static File resizeImage(Context context, Uri uri, int maxWidth, int maxHeight, int quality)
            throws IOException {

        FileOutputStream stream = null;
        try {
            final File file = getOutgoingPictureFile();
            stream = new FileOutputStream(file);
            resizeImage(context, uri, maxWidth, maxHeight,
                Bitmap.CompressFormat.JPEG, quality, stream);
            return file;
        }
        finally {
            try {
                stream.close();
            }
            catch (Exception e) {
                // ignored
            }
        }
    }

    public static void resizeImage(Context context, Uri uri, int maxWidth, int maxHeight,
            Bitmap.CompressFormat format, int quality, FileOutputStream output)
            throws IOException {

        final int MAX_IMAGE_SIZE = 1200000; // 1.2MP

        ContentResolver cr = context.getContentResolver();

        // compute optimal image scale size
        int scale = 1;
        InputStream in = cr.openInputStream(uri);

        try {
            // decode image size
            BitmapFactory.Options o = bitmapOptionsDecodeBounds();
            BitmapFactory.decodeStream(in, null, o);
            in.close();

            // calculate optimal image scale size
            while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) > MAX_IMAGE_SIZE)
                scale++;

            Log.d(TAG, "scale = " + scale + ", orig-width: " + o.outWidth + ", orig-height: " + o.outHeight);
        }
        catch (IOException e) {
            Log.d(TAG, "unable to calculate optimal scale size, using original image");
        }
        finally {
            try {
                in.close();
            }
            catch (Exception e) {
                // ignored
            }
        }

        // open image again for the actual scaling
        Bitmap bitmap = null;

        try {
            in = cr.openInputStream(uri);
            BitmapFactory.Options o = new BitmapFactory.Options();

            if (scale > 1) {
                o.inSampleSize = scale - 1;
            }

            bitmap = BitmapFactory.decodeStream(in, null, o);
        }
        finally {
            try {
                in.close();
            }
            catch (Exception e) {
                // ignored
            }
        }

        if (bitmap == null) {
            return;
        }
        float photoW = bitmap.getWidth();
        float photoH = bitmap.getHeight();
        if (photoW == 0 || photoH == 0) {
            return;
        }
        float scaleFactor = Math.max(photoW / maxWidth, photoH / maxHeight);
        int w = (int) (photoW / scaleFactor);
        int h = (int) (photoH / scaleFactor);
        if (h == 0 || w == 0) {
            return;
        }

        Bitmap scaledBitmap = null;
        try {
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        finally {
            if (scaledBitmap != bitmap)
                bitmap.recycle();
        }

        // check for rotation data
        Bitmap rotatedScaledBitmap = bitmapOrientation(context, uri, scaledBitmap);
        if (rotatedScaledBitmap != scaledBitmap)
            scaledBitmap.recycle();

        try {
            rotatedScaledBitmap.compress(format, quality, output);
        }
        finally {
            rotatedScaledBitmap.recycle();
        }
    }

    public static File copyOutgoingMedia(Context context, Uri media) throws IOException {
        final File outFile = getOutgoingPictureFile();
        InputStream in = context.getContentResolver().openInputStream(media);
        OutputStream out = null;
        try {
            out = new FileOutputStream(outFile);
            SystemUtils.copy(in, out);
            return outFile;
        }
        finally {
            SystemUtils.closeStream(in);
            SystemUtils.closeStream(out);
        }

    }

    /**
     * Returns true if the running platform is using SAF, therefore we'll need
     * to persist permissions when asking for media files.
     */
    public static boolean isStorageAccessFrameworkAvailable() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void requestPersistablePermissions(Context context, Uri uri) {
        context.getContentResolver().takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    public static void scanFile(Context context, File file, String mime) {
        MediaScannerConnection.scanFile(context.getApplicationContext(),
            new String[] { file.getPath() },
            new String[] { mime }, null);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void createFile(Fragment fragment, String mimeType, String fileName, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

        // Filter to only show results that can be "opened", such as
        // a file (as opposed to a list of contacts or timezones).
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Create a file with the requested MIME type.
        intent.setType(mimeType);
        // Note: This is not documented, but works
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        fragment.startActivityForResult(intent, requestCode);
    }

    public static synchronized void playNotificationSound(Context context, @RawRes int resId) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (!audioManager.isMusicActive() && audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            if (mMediaPlayer == null)
                mMediaPlayer = new QuickMediaPlayer();

            mMediaPlayer.play(context, resId);
        }
    }

    private static final class QuickMediaPlayer {
        private MediaPlayer mMediaPlayer;
        @RawRes
        private int mResId;

        public void play(Context context, @RawRes int resId) {
            if (mMediaPlayer != null && mResId == resId) {
                // same file, just play again
                mMediaPlayer.stop();
                try {
                    mMediaPlayer.prepare();
                    play();
                    return;
                }
                catch (IOException e) {
                    // will simply re-create the media player
                }
            }

            if (mMediaPlayer != null) {
                mMediaPlayer.release();
            }

            mMediaPlayer = create(context, resId);
            if (mMediaPlayer != null) {
                mResId = resId;
                play();
            }
        }

        private MediaPlayer create(Context context, int resId) {
            MediaPlayer player = new MediaPlayer();
            AssetFileDescriptor afd = null;
            try {
                player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);

                afd = context.getResources().openRawResourceFd(resId);
                if (afd != null) {
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    player.prepare();
                    return player;
                }
            }
            catch (Exception e) {
                player.release();
            }
            finally {
                SystemUtils.closeStream(afd);
            }

            return null;
        }

        private void play() {
            mMediaPlayer.start();
        }
    }

}
