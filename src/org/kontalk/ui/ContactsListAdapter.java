package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.data.Contact;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;


public class ContactsListAdapter extends CursorAdapter {

    private static final String TAG = ContactsListAdapter.class.getSimpleName();

    private final LayoutInflater mFactory;
    private OnContentChangedListener mOnContentChangedListener;

    public ContactsListAdapter(Context context, Cursor cursor) {
        super(context, cursor, false /* auto-requery */);
        mFactory = LayoutInflater.from(context);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (!(view instanceof ContactsListItem)) {
            Log.e(TAG, "Unexpected bound view: " + view);
            return;
        }

        ContactsListItem headerView = (ContactsListItem) view;
        Contact contact = Contact.fromRawContactCursor(context, cursor);
        headerView.bind(context, contact);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mFactory.inflate(R.layout.contacts_list_item, parent, false);
    }

    public interface OnContentChangedListener {
        void onContentChanged(ContactsListAdapter adapter);
    }

    public void setOnContentChangedListener(OnContentChangedListener l) {
        mOnContentChangedListener = l;
    }

    @Override
    protected void onContentChanged() {
        Cursor c = getCursor();
        if (c != null && !c.isClosed() && mOnContentChangedListener != null) {
            mOnContentChangedListener.onContentChanged(this);
        }
    }
}
