package org.kontalk.provider;

import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Bundle;


public class UsersCursor extends CursorWrapper {

    private Bundle mExtras = Bundle.EMPTY;

    public UsersCursor(Cursor cursor) {
        super(cursor);
    }

    @Override
    public Bundle getExtras() {
        return mExtras;
    }

    @Override
    public void setExtras(Bundle extras) {
        mExtras = (extras == null) ? Bundle.EMPTY : extras;
    }

}
