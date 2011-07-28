package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.client.AbstractMessage;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

public class MessageListAdapter extends CursorAdapter {

    private static final String TAG = MessageListAdapter.class.getSimpleName();

    private final LayoutInflater mFactory;
    private OnContentChangedListener mOnContentChangedListener;

    public MessageListAdapter(Context context, Cursor cursor) {
        super(context, cursor, false /* auto-requery */);
        mFactory = LayoutInflater.from(context);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (!(view instanceof MessageListItem)) {
            Log.e(TAG, "unexpected bound view: " + view);
            return;
        }

        MessageListItem headerView = (MessageListItem) view;
        AbstractMessage<?> msg = AbstractMessage.fromCursor(context, cursor);

        headerView.bind(context, msg);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mFactory.inflate(R.layout.message_list_item, parent, false);
    }

    public interface OnContentChangedListener {
        void onContentChanged(MessageListAdapter adapter);
    }

    public void setOnContentChangedListener(OnContentChangedListener l) {
        mOnContentChangedListener = l;
    }

    @Override
    protected void onContentChanged() {
        Cursor c = getCursor();
        Log.i(TAG, "content has changed (c=" + c + ")");
        if (c != null && !c.isClosed() && mOnContentChangedListener != null) {
            mOnContentChangedListener.onContentChanged(this);
        }
    }
}
