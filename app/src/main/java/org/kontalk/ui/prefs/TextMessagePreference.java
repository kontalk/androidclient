// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.kontalk.ui.prefs;

import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.widget.TextView;


/**
 * A preference that displays informational text.
 */
public class TextMessagePreference extends Preference {

    /**
     * Constructor for inflating from XML.
     */
    public TextMessagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSelectable(false);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView textView = (TextView) holder.findViewById(android.R.id.title);
        textView.setSingleLine(false);
        textView.setMaxLines(Integer.MAX_VALUE);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
