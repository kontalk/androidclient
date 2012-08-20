package org.kontalk.ui;

import android.app.ProgressDialog;
import android.content.Context;


public class NonSearchableProgressDialog extends ProgressDialog {

    public NonSearchableProgressDialog(Context context) {
        super(context);
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

}
