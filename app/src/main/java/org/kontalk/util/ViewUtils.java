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

import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vanniktech.emoji.EmojiManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;


/**
 * View utilities
 * @author andreacappelli
 */
public class ViewUtils {

    public static int dp(Context context, float value) {
        if (value == 0) {
            return 0;
        }
        return (int) Math.ceil(context.getResources().getDisplayMetrics().density * value);
    }

    /**
     * Waits for global layout to happen and set a QR code for the minimum size
     * between height and width of the given container. The QR code will be
     * loaded automatically in {@code destination}.
     */
    public static void getQRCodeBitmapAsync(final Context context, final View container, final ImageView destination, final String text) {
        // no need to handle memory leaks because the view tree observer
        // is created on every activity restart
        container.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int size = Math.min(container.getHeight(), container.getWidth());
                if (size == 0) {
                    // try display size as last resort
                    Point displaySize = SystemUtils.getDisplaySize(context);
                    size = Math.min(displaySize.x, displaySize.y);
                }

                try {
                    // TODO load in another thread
                    Bitmap qrCode = getQRCodeBitmap(size, text);
                    destination.setImageBitmap(qrCode);
                }
                catch (WriterException e) {
                    // TODO set error image on destination
                }
            }
        });
    }

    public static Bitmap getQRCodeBitmap(int size, String text) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 2);
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);
        return toBitmap(matrix);
    }

    /**
     * Writes the given Matrix on a new Bitmap object.
     * http://codeisland.org/2013/generating-qr-codes-with-zxing/
     * @param matrix the matrix to write.
     * @return the new {@link Bitmap}-object.
     */
    private static Bitmap toBitmap(BitMatrix matrix){
        int height = matrix.getHeight();
        int width = matrix.getWidth();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++){
            for (int y = 0; y < height; y++){
                bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    public static CharSequence injectEmojis(TextView view, CharSequence text) {
        final SpannableStringBuilder emojiSpannable = new SpannableStringBuilder(text);
        final Paint.FontMetrics fontMetrics = view.getPaint().getFontMetrics();
        final float defaultEmojiSize = fontMetrics.descent - fontMetrics.ascent;
        EmojiManager.getInstance().replaceWithImages(view.getContext(),
            emojiSpannable, defaultEmojiSize, defaultEmojiSize);
        return emojiSpannable;
    }

}
