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

import org.kontalk.R;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.EditText;


/**
 * A custom {@link AlertDialog} with an {@link EditText} inside.
 * @author Daniele Ricci
 */
public class InputDialog extends AlertDialog {

	private static final int TEXT_VIEW_ID = R.id.textinput;

	public InputDialog(Context context) {
		super(context);
	}

	public InputDialog(Context context, int theme) {
		super(context, theme);
	}

	public InputDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
		super(context, cancelable, cancelListener);
	}

	public CharSequence getText() {
		return getTextFromAlertDialog(this);
	}

	public static CharSequence getTextFromAlertDialog(AlertDialog dialog) {
		return ((EditText) dialog.findViewById(TEXT_VIEW_ID)).getText();
	}

	public static class Builder extends AlertDialog.Builder {

		public Builder(Context context, int inputType) {
			super(context);

			setCustomView(context, inputType);
		}

		private void setCustomView(Context context, int inputType) {
			/*
			EditText txt = new EditText(context);
			txt.setInputType(inputType);
			txt.setId(TEXT_VIEW_ID);
			*/

			setView(LayoutInflater.from(context)
				.inflate(R.layout.edittext_dialog, null, false));
		}

		@Override
		public AlertDialog create() {
			return super.create();
		}

	}

}
