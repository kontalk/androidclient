/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui;

import com.afollestad.materialdialogs.MaterialDialog;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.kontalk.R;


/**
 * Wrapper for a dialog with a checkbox.
 * @author Daniele Ricci
 */
public class CheckboxAlertDialog extends MaterialDialog {

    protected CheckboxAlertDialog(Builder builder) {
        super(builder);
    }

    public static class Builder extends MaterialDialog.Builder {

        public Builder(@NonNull Context context) {
            super(context);
            customView(R.layout.md_dialog_checkbox, false);
        }

        @Override
        public MaterialDialog.Builder content(@StringRes int contentRes, Object... formatArgs) {
            ((TextView) customView.findViewById(R.id.content))
                .setText(context.getString(contentRes, formatArgs));
            return this;
        }

        @Override
        public Builder content(@StringRes int contentRes) {
            ((TextView) customView.findViewById(R.id.content))
                .setText(contentRes);
            return this;
        }

        @Override
        public Builder content(@NonNull CharSequence content) {
            ((TextView) customView.findViewById(R.id.content))
                .setText(content);
            return this;
        }

        public Builder checkboxText(@StringRes int contentRes) {
            ((CheckBox) customView.findViewById(R.id.checkbox))
                .setText(contentRes);
            return this;
        }

        public Builder checkboxText(@NonNull CharSequence content) {
            ((CheckBox) customView.findViewById(R.id.checkbox))
                .setText(content);
            return this;
        }

        public Builder onCheckboxChanged(@NonNull CompoundButton.OnCheckedChangeListener listener) {
            ((CheckBox) customView.findViewById(R.id.checkbox))
                .setOnCheckedChangeListener(listener);
            return this;
        }
    }

}
