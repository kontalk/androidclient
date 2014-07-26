/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.kontalk.R;


/**
 * A password input dialog supporting double input with verification.
 * @author Daniele Ricci
 */
public class PasswordInputDialog {

    private PasswordInputDialog() {
    }

    public interface OnPasswordInputListener {
        public void onClick(DialogInterface dialog, int which, String password);
    }

    public static class Builder extends AlertDialog.Builder {

        private Context mContext;
        private EditText mPassword1;
        private EditText mPassword2;
        private OnPasswordInputListener mListener;
        private int mMinLength;

        public Builder(Context context) {
            this(context, 0, 0);
        }

        public Builder(Context context, int prompt1, int prompt2) {
            super(context);

            mContext = context;
            setCustomView(prompt1, prompt2);
        }
        
        public Builder setMinLength(int length) {
            mMinLength = length;
            return this;
        }

        public Builder setTitle(CharSequence title) {
            return (Builder) super.setTitle(title);
        }

        public Builder setTitle(int titleId) {
            return (Builder) super.setTitle(titleId);
        }

        public Builder setPositiveButton(int textId, OnPasswordInputListener listener) {
            return setPositiveButton(mContext.getText(textId), listener);
        }

        public Builder setPositiveButton(CharSequence text, final OnPasswordInputListener listener) {
            mListener = listener;
            return (Builder) super.setPositiveButton(text, null);
        }

        @Override
        public AlertDialog create() {
            final AlertDialog dialog = super.create();

            if (mListener != null) {
                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    public void onShow(DialogInterface iDialog) {
                        Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        button.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                String password1 = mPassword1.getText().toString();
                                if (password1.length() < mMinLength) {
                                    error(mContext.getString(R.string.err_password_too_short, mMinLength));
                                    return;
                                }

                                if (password1.equals(mPassword2.getText().toString())) {
                                    mListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE, password1);
                                    dialog.dismiss();
                                }
                                else {
                                    error(R.string.err_passwords_mismatch);
                                }
                            }
                        });
                    }
                });
            }

            return dialog;
        }

        private void error(int textId) {
            error(mContext.getText(textId));
        }

        private void error(CharSequence text) {
            Toast.makeText(mContext, text,
                Toast.LENGTH_SHORT).show();
            mPassword1.setText("");
            mPassword2.setText("");
        }

        private void setCustomView(int prompt1, int prompt2) {
            View view = LayoutInflater.from(mContext)
                .inflate(R.layout.password_dialog, null, false);

            mPassword1 = (EditText) view.findViewById(R.id.password1);
            mPassword2 = (EditText) view.findViewById(R.id.password2);

            if (prompt1 > 0)
                ((TextView) view.findViewById(R.id.prompt1)).setText(prompt1);
            if (prompt2 > 0)
                ((TextView) view.findViewById(R.id.prompt2)).setText(prompt2);

            setView(view);
        }

    }

}
