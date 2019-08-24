/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.providers.contacts;

import java.util.Locale;

import android.os.Build;
import androidx.core.text.ICUCompat;
import android.text.TextUtils;

public class LocaleSet {
    private static final String CHINESE_LANGUAGE = Locale.CHINESE.getLanguage().toLowerCase();

    private static class LocaleWrapper {
        private final Locale mLocale;
        private final String mLanguage;

        public LocaleWrapper(Locale locale) {
            mLocale = locale;
            if (mLocale != null) {
                mLanguage = mLocale.getLanguage().toLowerCase();
            } else {
                mLanguage = null;
            }
        }

        public boolean hasLocale() {
            return mLocale != null;
        }

        public Locale getLocale() {
            return mLocale;
        }

        public boolean isLocale(Locale locale) {
            return mLocale == null ? (locale == null) : mLocale.equals(locale);
        }

        public boolean isLanguage(String language) {
            return mLanguage == null ? (language == null)
                : mLanguage.equalsIgnoreCase(language);
        }

        public String toString() {
            return mLocale != null ? toBcp47Language(mLocale) : "(null)";
        }
    }

    public static LocaleSet getDefault() {
        return new LocaleSet(Locale.getDefault());
    }

    public LocaleSet(Locale locale) {
        this(locale, null);
    }

    private final LocaleWrapper mPrimaryLocale;
    private final LocaleWrapper mSecondaryLocale;

    public LocaleSet(Locale primaryLocale, Locale secondaryLocale) {
        mPrimaryLocale = new LocaleWrapper(primaryLocale);
        mSecondaryLocale = new LocaleWrapper(
                mPrimaryLocale.equals(secondaryLocale) ? null : secondaryLocale);
    }

    public boolean hasSecondaryLocale() {
        return mSecondaryLocale.hasLocale();
    }

    public Locale getPrimaryLocale() {
        return mPrimaryLocale.getLocale();
    }

    public Locale getSecondaryLocale() {
        return mSecondaryLocale.getLocale();
    }

    public boolean isPrimaryLocale(Locale locale) {
        return mPrimaryLocale.isLocale(locale);
    }

    public boolean isSecondaryLocale(Locale locale) {
        return mSecondaryLocale.isLocale(locale);
    }

    private static final String SCRIPT_SIMPLIFIED_CHINESE = "Hans";

    public static boolean isLocaleSimplifiedChinese(Locale locale) {
        // language must match
        if (locale == null || !TextUtils.equals(locale.getLanguage(), CHINESE_LANGUAGE)) {
            return false;
        }

        // script is optional but if present must match
        if (!TextUtils.isEmpty(ICUCompat.maximizeAndGetScript(locale))) {
            return ICUCompat.maximizeAndGetScript(locale).equals(SCRIPT_SIMPLIFIED_CHINESE);
        }
        // if no script, must match known country
        return locale.equals(Locale.SIMPLIFIED_CHINESE);
    }

    public boolean isPrimaryLocaleSimplifiedChinese() {
        return isLocaleSimplifiedChinese(getPrimaryLocale());
    }

    public boolean isSecondaryLocaleSimplifiedChinese() {
        return isLocaleSimplifiedChinese(getSecondaryLocale());
    }

    public boolean isPrimaryLanguage(String language) {
        return mPrimaryLocale.isLanguage(language);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof LocaleSet) {
            final LocaleSet other = (LocaleSet) object;
            return other.isPrimaryLocale(mPrimaryLocale.getLocale())
                && other.isSecondaryLocale(mSecondaryLocale.getLocale());
        }
        return false;
    }

    @Override
    public final String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(mPrimaryLocale.toString());
        if (hasSecondaryLocale()) {
            builder.append(";");
            builder.append(mSecondaryLocale.toString());
        }
        return builder.toString();
    }

    /**
     * Modified from:
     * https://github.com/apache/cordova-plugin-globalization/blob/master/src/android/Globalization.java
     *
     * Returns a well-formed ITEF BCP 47 language tag representing this locale string
     * identifier for the client's current locale
     *
     * @return String: The BCP 47 language tag for the current locale
     */
    private static String toBcp47Language(Locale loc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return loc.toLanguageTag();
        }

        // we will use a dash as per BCP 47
        final char SEP = '-';
        String language = loc.getLanguage();
        String region = loc.getCountry();
        String variant = loc.getVariant();

        // special case for Norwegian Nynorsk since "NY" cannot be a variant as per BCP 47
        // this goes before the string matching since "NY" wont pass the variant checks
        if (language.equals("no") && region.equals("NO") && variant.equals("NY")) {
            language = "nn";
            region = "NO";
            variant = "";
        }

        if (language.isEmpty() || !language.matches("\\p{Alpha}{2,8}")) {
            language = "und";       // Follow the Locale#toLanguageTag() implementation
            // which says to return "und" for Undetermined
        } else if (language.equals("iw")) {
            language = "he";        // correct deprecated "Hebrew"
        } else if (language.equals("in")) {
            language = "id";        // correct deprecated "Indonesian"
        } else if (language.equals("ji")) {
            language = "yi";        // correct deprecated "Yiddish"
        }

        // ensure valid country code, if not well formed, it's omitted
        if (!region.matches("\\p{Alpha}{2}|\\p{Digit}{3}")) {
            region = "";
        }

        // variant subtags that begin with a letter must be at least 5 characters long
        if (!variant.matches("\\p{Alnum}{5,8}|\\p{Digit}\\p{Alnum}{3}")) {
            variant = "";
        }

        StringBuilder bcp47Tag = new StringBuilder(language);
        if (!region.isEmpty()) {
            bcp47Tag.append(SEP).append(region);
        }
        if (!variant.isEmpty()) {
            bcp47Tag.append(SEP).append(variant);
        }

        return bcp47Tag.toString();
    }
}
