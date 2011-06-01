package org.nuntius.ui;

import android.app.ProgressDialog;
import android.content.Context;


/**
 * A progress dialog that can't be dismissed.
 * @author Daniele Ricci
 * @version 1.0
 */
public class LockedDialog extends ProgressDialog {

    public LockedDialog(Context ctx) {
        super(ctx);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    public boolean onSearchRequested() {
        return true;
    }
}
