/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import org.jivesoftware.smack.util.Async;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import org.kontalk.R;


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
     * returned to the given listener.
     */
    public static void getQRCodeBitmapAsync(final Context context, final View container, final String text, final OnQRCodeGeneratedListener listener) {
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

                final int qrSize = size;
                Async.go(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Bitmap qrCode = getQRCodeBitmap(qrSize, text);
                            container.post(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onQRCodeGenerated(qrCode);
                                }
                            });
                        }
                        catch (WriterException e) {
                            listener.onQRCodeError(e);
                        }
                    }
                });
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

    public interface OnQRCodeGeneratedListener {
        void onQRCodeGenerated(Bitmap qrCode);

        void onQRCodeError(Exception e);
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

    /**
     * Sets the text style based on the text size user preference.
     * @param textView the view to apply the style
     * @param applyBaseTheme true to apply a base TextAppearance first
     * @deprecated applyBaseTheme should not exist, a style is the solution here
     */
    @Deprecated
    public static void setMessageBodyTextStyle(TextView textView, boolean applyBaseTheme) {
        Context context = textView.getContext();
        String size = Preferences.getFontSize(context);
        int sizeId;
        switch (size) {
            case "small":
                sizeId = R.dimen.message_font_size_small;
                break;
            case "large":
                sizeId = R.dimen.message_font_size_large;
                break;
            default:
                sizeId = R.dimen.message_font_size_normal;
                break;
        }

        if (applyBaseTheme) {
            // set a baseline theme
            int[] attrs = {android.R.attr.textAppearance};
            TypedArray ta = context.getTheme().obtainStyledAttributes(R.style.AppTheme, attrs);
            TextViewCompat.setTextAppearance(textView, ta.getResourceId(0, 0));
            ta.recycle();
        }

        // now apply the text size
        float textSize = context.getResources().getDimension(sizeId);
        if (textSize > 0)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
    }

}
