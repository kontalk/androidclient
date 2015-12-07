/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.lang.Character.UnicodeBlock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


/**
 * This utility class provides specialized handling for locale specific
 * information: labels, name lookup keys.
 * Modified to work with IBM ICU4J.
 */
public class ContactLocaleUtils {
    public static final String TAG = "ContactLocale";

    /**
     * This class is the default implementation and should be the base class
     * for other locales.
     *
     * sortKey: same as name
     * nameLookupKeys: none
     * labels: uses ICU AlphabeticIndex for labels and extends by labeling
     *     phone numbers "#".  Eg English labels are: [A-Z], #, " "
     */
    private static class ContactLocaleUtilsBase {
        private static final String EMPTY_STRING = "";
        private static final String NUMBER_STRING = "#";

        public String getBucketLabel(String name) {
            if (name.length() == 0)
                return EMPTY_STRING;

            boolean prefixIsNumeric = false;
            final int length = name.length();
            int offset = 0;
            while (offset < length) {
                int codePoint = Character.codePointAt(name, offset);
                // Ignore standard phone number separators and identify any
                // string that otherwise starts with a number.
                if (Character.isDigit(codePoint)) {
                    prefixIsNumeric = true;
                    break;
                } else if (!Character.isSpaceChar(codePoint) &&
                           codePoint != '+' && codePoint != '(' &&
                           codePoint != ')' && codePoint != '.' &&
                           codePoint != '-' && codePoint != '#') {
                    break;
                }
                offset += Character.charCount(codePoint);
            }
            if (prefixIsNumeric) {
                return NUMBER_STRING;
            }

            return name.substring(0, name.offsetByCodePoints(0, 1));
        }
    }

    /**
     * Japanese specific locale overrides.
     *
     * sortKey: unchanged (same as name)
     * nameLookupKeys: unchanged (none)
     * labels: extends default labels by labeling unlabeled CJ characters
     *     with the Japanese character 他 ("misc"). Japanese labels are:
     *     あ, か, さ, た, な, は, ま, や, ら, わ, 他, [A-Z], #, " "
     */
    private static class JapaneseContactUtils extends ContactLocaleUtilsBase {
        // \u4ed6 is Japanese character 他 ("misc")
        private static final String JAPANESE_MISC_LABEL = "\u4ed6";

        public JapaneseContactUtils() {
            super();
        }

        // Set of UnicodeBlocks for unified CJK (Chinese) characters and
        // Japanese characters. This includes all code blocks that might
        // contain a character used in Japanese (which is why unified CJK
        // blocks are included but Korean Hangul and jamo are not).
        private static final Set<Character.UnicodeBlock> CJ_BLOCKS;
        static {
            Set<UnicodeBlock> set = new HashSet<>();
            set.add(UnicodeBlock.HIRAGANA);
            set.add(UnicodeBlock.KATAKANA);
            set.add(UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS);
            set.add(UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS);
            set.add(UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
            set.add(UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A);
            set.add(UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B);
            set.add(UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION);
            set.add(UnicodeBlock.CJK_RADICALS_SUPPLEMENT);
            set.add(UnicodeBlock.CJK_COMPATIBILITY);
            set.add(UnicodeBlock.CJK_COMPATIBILITY_FORMS);
            set.add(UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS);
            set.add(UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT);
            CJ_BLOCKS = Collections.unmodifiableSet(set);
        }

        /**
         * Helper routine to identify unlabeled Chinese or Japanese characters
         * to put in a 'misc' bucket.
         *
         * @return true if the specified Unicode code point is Chinese or
         *              Japanese
         */
        private static boolean isChineseOrJapanese(int codePoint) {
            return CJ_BLOCKS.contains(UnicodeBlock.of(codePoint));
        }

        @Override
        public String getBucketLabel(String name) {
            if (!isChineseOrJapanese(Character.codePointAt(name, 0)))
                return JAPANESE_MISC_LABEL;
            return super.getBucketLabel(name);
        }
    }

    /**
     * Simplified Chinese specific locale overrides. Uses ICU Transliterator
     * for generating pinyin transliteration.
     *
     * sortKey: unchanged (same as name)
     * nameLookupKeys: adds additional name lookup keys
     *     - Chinese character's pinyin and pinyin's initial character.
     *     - Latin word and initial character.
     * labels: unchanged
     *     Simplified Chinese labels are the same as English: [A-Z], #, " "
     */
    private static class SimplifiedChineseContactUtils
        extends ContactLocaleUtilsBase {
        public SimplifiedChineseContactUtils() {
            super();
        }
    }

    private static final String JAPANESE_LANGUAGE = Locale.JAPANESE.getLanguage().toLowerCase();

    private static ContactLocaleUtils sSingleton;

    private final LocaleSet mLocales;
    private final ContactLocaleUtilsBase mUtils;

    private ContactLocaleUtils(LocaleSet locales) {
        if (locales == null) {
            mLocales = LocaleSet.getDefault();
        } else {
            mLocales = locales;
        }
        if (mLocales.isPrimaryLanguage(JAPANESE_LANGUAGE)) {
            mUtils = new JapaneseContactUtils();
        } else if (mLocales.isPrimaryLocaleSimplifiedChinese()) {
            mUtils = new SimplifiedChineseContactUtils();
        } else {
            mUtils = new ContactLocaleUtilsBase();
        }
    }

    public boolean isLocale(LocaleSet locales) {
        return mLocales.equals(locales);
    }

    public static synchronized ContactLocaleUtils getInstance() {
        if (sSingleton == null) {
            sSingleton = new ContactLocaleUtils(LocaleSet.getDefault());
        }
        return sSingleton;
    }

    public static synchronized void setLocale(Locale locale) {
        setLocales(new LocaleSet(locale));
    }

    public static synchronized void setLocales(LocaleSet locales) {
        if (sSingleton == null || !sSingleton.isLocale(locales)) {
            sSingleton = new ContactLocaleUtils(locales);
        }
    }

    public String getLabel(String name) {
        return mUtils.getBucketLabel(name);
    }
}
