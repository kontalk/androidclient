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

package org.kontalk.ui.view;

import java.util.regex.Pattern;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;


/**
 * A message list item to be used in {@link org.kontalk.ui.ComposeMessage} activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageListItem extends RelativeLayout implements Checkable {

    private CompositeMessage mMessage;
    // for message details
    private String mPeer;

    private MessageListItemTheme mBalloonTheme;

    private TextView mDateHeader;

    private boolean mChecked;

    /*
    private LeadingMarginSpan mLeadingMarginSpan;

    private LineHeightSpan mSpan = new LineHeightSpan() {
        public void chooseHeight(CharSequence text, int start,
                int end, int spanstartv, int v, FontMetricsInt fm) {
            fm.ascent -= 10;
        }
    };

    private TextAppearanceSpan mTextSmallSpan =
        new TextAppearanceSpan(getContext(), android.R.style.TextAppearance_Small);
    */

    public MessageListItem(Context context) {
        super(context);
    }

    public MessageListItem(final Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDateHeader = (TextView) findViewById(R.id.date_header);
    }

    public void afterInflate(int direction) {
        ViewStub stub = (ViewStub) findViewById(R.id.balloon_stub);
        String theme = Preferences.getBalloonTheme(getContext());
        mBalloonTheme = MessageListItemThemeFactory.createTheme(theme, direction);
        mBalloonTheme.inflate(stub);
    }

    public final void bind(Context context, final CompositeMessage msg,
       final Contact contact, final Pattern highlight, long previous,
       Object... args) {

        mMessage = msg;

        // FIXME this might not work
        mChecked = false;

        long msgTs = MessageUtils.getMessageTimestamp(mMessage);
        if (MessageUtils.isSameDate(msgTs, previous)) {
            mDateHeader.setVisibility(View.GONE);
        }
        else {
            mDateHeader.setText(MessageUtils.formatDateString(context, msgTs));
            mDateHeader.setVisibility(View.VISIBLE);
        }

        mBalloonTheme.setSecurityFlags(mMessage.getSecurityFlags());

        if (mMessage.getSender() != null) {
            mBalloonTheme.setIncoming(contact);

            Contact c = Contact.findByUserId(context, msg.getSender(true));
            if (c != null)
                mPeer = c.getNumber();
            if (mPeer == null)
                mPeer = msg.getSender(true);
        }
        else {
            mBalloonTheme.setOutgoing(contact, mMessage.getStatus());
            Contact c = Contact.findByUserId(context, msg.getRecipients().get(0));
            if (c != null)
                mPeer = c.getNumber();
            if (mPeer == null)
                mPeer = msg.getRecipients().get(0);
        }

        mBalloonTheme.setTimestamp(formatTimestamp());

        if (msg.isEncrypted()) {
            mBalloonTheme.setEncryptedContent(mMessage.getDatabaseId());
        }

        else {
            // process components
            Object[] argsAppend = null;
            if (args != null) {
                argsAppend = new Object[args.length+1];
                System.arraycopy(args, 0, argsAppend, 0, args.length);
                argsAppend[args.length] = mBalloonTheme;
            }
            mBalloonTheme.processComponents(mMessage.getDatabaseId(),
                highlight, msg.getComponents(), argsAppend);
        }
    }

    /*
    private SpannableStringBuilder formatMessage(final Contact contact, final Pattern highlight) {
        SpannableStringBuilder buf;

        if (mMessage.isEncrypted()) {
            buf = new SpannableStringBuilder(getResources().getString(R.string.text_encrypted));
        }
        else {
            // this is used later to add \n at the end of the image placeholder
            boolean thumbnailOnly;

            TextComponent txt = (TextComponent) mMessage.getComponent(TextComponent.class);

            String textContent = txt != null ? txt.getContent() : null;

            if (TextUtils.isEmpty(textContent)) {
                buf = new SpannableStringBuilder();
                thumbnailOnly = true;
            }

            else {
                buf = new SpannableStringBuilder(textContent);
                thumbnailOnly = false;
            }

            // convert smileys first
            int c = buf.length();
            if (c > 0 && c < MAX_AFFORDABLE_SIZE)
                MessageUtils.convertSmileys(getContext(), buf, SmileyImageSpan.SIZE_EDITABLE);

            // image component: show image before text
            AttachmentComponent attachment = (AttachmentComponent) mMessage
                    .getComponent(AttachmentComponent.class);

            if (attachment != null) {

                if (attachment instanceof ImageComponent) {
                    ImageComponent img = (ImageComponent) attachment;

                    // prepend some text for the ImageSpan
                    String placeholder = CompositeMessage.getSampleTextContent(img.getContent().getMime());
                    buf.insert(0, placeholder);

                    // add newline if there is some text after
                    if (!thumbnailOnly)
                        buf.insert(placeholder.length(), "\n");

                    Bitmap bitmap = img.getBitmap();
                    if (bitmap != null) {
                        ImageSpan imgSpan = new MaxSizeImageSpan(getContext(), bitmap);
                        buf.setSpan(imgSpan, 0, placeholder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                }

                else {

                    // other component: show sample content if no body was found
                    if (txt == null)
                        buf.append(CompositeMessage.getSampleTextContent(attachment.getMime()));

                }

            }

        }

        if (highlight != null) {
            Matcher m = highlight.matcher(buf.toString());
            while (m.find())
                buf.setSpan(mHighlightColorSpan, m.start(), m.end(), 0);
        }

        return buf;
    }
    */

    private CharSequence formatTimestamp() {
        return MessageUtils.formatTimeString(getContext(), MessageUtils.getMessageTimestamp(mMessage));
    }

    public final void unbind() {
        // TODO mMessage.recycle();
        mMessage = null;
        mBalloonTheme.unload();
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        mChecked = checked;

        int backgroundId;

        if (mChecked)
            backgroundId = R.drawable.list_selected_holo_light;
        else
            backgroundId = 0;

        setBackgroundResource(backgroundId);
    }

    @Override
    public void toggle() {
        setChecked(!mChecked);
    }

    // Thanks to Google Mms app :)
    public void onClick() {
        TextContentView textContent = mBalloonTheme.getTextContentView();

        if (textContent == null)
            return;

        // Check for links. If none, do nothing; if 1, open it; if >1, ask user to pick one
        final URLSpan[] spans = textContent.getUrls();

        if (spans.length == 0) {
            // show the message details dialog
            MessageUtils.showMessageDetails(getContext(), mMessage, mPeer);
        }
        else if (spans.length == 1) {
            // show link opener
            spans[0].onClick(textContent);
        }
        else {
            // complex stuff (media)
            ArrayAdapter<URLSpan> adapter =
                new ArrayAdapter<URLSpan>(getContext(), android.R.layout.select_dialog_item, spans) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        Context context = getContext();
                        try {
                            URLSpan span = getItem(position);
                            String url = span.getURL();
                            Uri uri = Uri.parse(url);
                            TextView tv = (TextView) v;
                            Drawable d = context.getPackageManager().getActivityIcon(
                                new Intent(Intent.ACTION_VIEW, uri));
                            if (d != null) {
                                d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
                                tv.setCompoundDrawablePadding(10);
                                tv.setCompoundDrawables(d, null, null, null);
                            }
                            final String telPrefix = "tel:";
                            if (url.startsWith(telPrefix)) {
                                // TODO handle country code
                                url = url.substring(telPrefix.length());
                            }
                            tv.setText(url);
                        } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                            // it's ok if we're unable to set the drawable for this view - the user
                            // can still use it
                        }
                        return v;
                    }
                };

            AlertDialogWrapper.Builder b = new AlertDialogWrapper.Builder(getContext());

            final TextContentView textView = textContent;
            DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    if (which >= 0) {
                        spans[which].onClick(textView);
                    }
                    dialog.dismiss();
                }
            };

            b.setTitle(R.string.chooser_select_link);
            b.setCancelable(true);
            b.setAdapter(adapter, click);

            b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            b.show();
        }
    }

    public CompositeMessage getMessage() {
        return mMessage;
    }
}
