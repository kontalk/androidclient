/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;


/**
 * A custom {@link AlertDialog} with an {@link EditText} inside.
 * This isn't really a dialog class, it is rather a container for a custom
 * dialog builder which adds some features over the default one.
 * @author Daniele Ricci
 * @deprecated {@link MaterialDialog} should be able to handle passwords as well.
 */
@Deprecated
public class InputDialog {

    private InputDialog() {
    }

    @Deprecated
    public static class Builder extends MaterialDialog.Builder {

        public Builder(Context context, int inputType) {
            super(context);
            inputType(inputType);
        }

    }

}
