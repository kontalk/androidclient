package org.kontalk.ui;

import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;


/**
 * The composer fragment.
 * @author Daniele Ricci
 */
public class ComposeMessageFragment extends Fragment {

    /** Returns a new fragment instance from a picked contact. */
    public static ComposeMessageFragment fromContactPicker(Context context, Uri rawContactUri) {
        String userId = Contact.getUserId(context, rawContactUri);
        if (userId != null) {
            ComposeMessageFragment f = new ComposeMessageFragment();
            Conversation conv = Conversation.loadFromUserId(context, userId);
            // not found - create new
            if (conv == null) {
                Bundle args = new Bundle();
                args.putString("action", ComposeMessage.ACTION_VIEW_USERID);
                args.putParcelable("data", Threads.getUri(userId));
                f.setArguments(args);
                return f;
            }

            return fromConversation(context, conv);
        }

        return null;
    }

    /** Returns a new fragment instance from a {@link Conversation} instance. */
    public static ComposeMessageFragment fromConversation(Context context, Conversation conv) {
        return fromConversation(context, conv.getThreadId());
    }

    /** Returns a new fragment instance from a thread ID. */
    public static ComposeMessageFragment fromConversation(Context context, long threadId) {
        ComposeMessageFragment f = new ComposeMessageFragment();
        Bundle args = new Bundle();
        args.putString("action", ComposeMessage.ACTION_VIEW_CONVERSATION);
        args.putParcelable("data", ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
        f.setArguments(args);
        return f;
    }

}
