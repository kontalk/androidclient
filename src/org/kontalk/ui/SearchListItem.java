package org.kontalk.ui;

import org.kontalk.data.Contact;
import org.kontalk.data.SearchItem;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;


public class SearchListItem extends RelativeLayout {
    //private static final String TAG = SearchListItem.class.getSimpleName();

    private SearchItem mFound;
    private TextView mText1;
    private TextView mText2;

    public SearchListItem(Context context) {
        super(context);
    }

    public SearchListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mText1 = (TextView) findViewById(android.R.id.text1);
        mText2 = (TextView) findViewById(android.R.id.text2);

        if (isInEditMode()) {
            mText1.setText("Test contact");
            mText2.setText("...hello buddy! How...");
        }
    }

    public final void bind(Context context, final SearchItem found) {
        mFound = found;

        final Contact contact = found.getContact();
        String name;
        if (contact != null)
            name = contact.getName() + " <" + contact.getNumber() + ">";
        else
            name = found.getUserId();

        mText1.setText(name);
        mText2.setText(found.getText());
    }

    public final void unbind() {
        // TODO unbind (?)
    }

    public SearchItem getSearchItem() {
        return mFound;
    }

}
