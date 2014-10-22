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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.crypto.Coder;
import org.kontalk.data.Contact;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.service.DownloadService;
import org.kontalk.service.UploadService;

import java.util.regex.Pattern;


/**
 * Message component for {@link ImageComponent}.
 * @author Daniele Ricci
 */
public class ImageContentView extends RelativeLayout
        implements MessageContentView<ImageComponent>, BalloonProgressControl, View.OnClickListener {

    private ImageComponent mComponent;
    private ImageView mImage;
    private ImageView mDownloadButton;
    private ImageView mUploadButton;
    private ProgressBar mProgressBar;

    private BalloonProgress mBalloonProgress;

    private Long mMessageId;
    private String mMessageSender;

    private IntentFilter mDownloadFilter =new IntentFilter(DownloadService.INTENT_ACTION);
    private IntentFilter mUploadFilter = new IntentFilter(UploadService.INTENT_ACTION);
    private LocalBroadcastManager mLbm;

    public ImageContentView(Context context) {
        super(context);
    }

    public ImageContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private BroadcastReceiver mDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int progress = intent.getIntExtra(DownloadService.INTENT_PROGRESS, -1);
            long msgId = intent.getLongExtra(DownloadService.INTENT_MSGID, -1);
            if (mMessageId == msgId)
                mProgressBar.setProgress(progress);
        }
    };

    private BroadcastReceiver mUploadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int progress = intent.getIntExtra(DownloadService.INTENT_PROGRESS, -1);
            long msgId = intent.getLongExtra(DownloadService.INTENT_MSGID, -1);
            if (mMessageId == msgId)
                mProgressBar.setProgress(progress);
        }
    };

    public void bind(long messageId, String messageSender, ImageComponent component, Contact contact, Pattern highlight) {
        mComponent = component;
        mMessageId = messageId;
        mMessageSender = messageSender;

        if(mProgressBar == null)
            mProgressBar = new ProgressBar(getContext());

        mProgressBar = (ProgressBar) findViewById(R.id.balloon_progress);
        mImage = (ImageView) findViewById(R.id.image_balloon);
        mDownloadButton = (ImageView) findViewById(R.id.download_button);
        mUploadButton = (ImageView) findViewById(R.id.upload_button);

        boolean fetched = component.getLocalUri() != null;

        // prepend some text for the ImageSpan
        //String placeholder = CompositeMessage.getSampleTextContent(component.getContent().getMime());

        Bitmap bitmap = mComponent.getBitmap();
        if (bitmap != null)
            mImage.setImageBitmap(bitmap);

        // TODO else: maybe some placeholder like Image: image/jpeg

        mUploadButton.setVisibility(GONE);
        mDownloadButton.setVisibility(fetched ? GONE : VISIBLE);
        mProgressBar.setVisibility(GONE);

        if(DownloadService.isQueued(mMessageId)) {
            mLbm = LocalBroadcastManager.getInstance(getContext());
            mLbm.registerReceiver(mDownloadReceiver, mDownloadFilter);
            mDownloadButton.setBackgroundResource(R.drawable.attachement_cancel);
            mProgressBar.setVisibility(VISIBLE);
        }

        if (UploadService.isQueued(mMessageId)) {
            mLbm = LocalBroadcastManager.getInstance(getContext());
            mLbm.registerReceiver(mUploadReceiver, mUploadFilter);
            mUploadButton.setVisibility(VISIBLE);
            mUploadButton.setBackgroundResource(R.drawable.attachement_cancel);
            mProgressBar.setVisibility(VISIBLE);
        }

        if (DownloadService.isQueued(mMessageId))
            mBalloonProgress.onBind(this, getContext(), mDownloadReceiver);
        if (UploadService.isQueued(mMessageId))
            mBalloonProgress.onBind(this, getContext(), mUploadReceiver);

        mDownloadButton.setOnClickListener(this);
    }

    public void unbind() {
        clear();
        if (DownloadService.isQueued(mMessageId)  && mLbm != null)
            mLbm.unregisterReceiver(mDownloadReceiver);
        if (UploadService.isQueued(mMessageId) && mLbm != null)
            mLbm.unregisterReceiver(mUploadReceiver);
        mLbm = null;
    }

    public ImageComponent getComponent() {
        return mComponent;
    }

    /** Image is always on top. */
    @Override
    public int getPriority() {
        return 1;
    }

    private void clear() {
        mComponent = null;
        mImage.setImageBitmap(null);
    }

    public static ImageContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (ImageContentView) inflater.inflate(R.layout.message_content_image,
            parent, false);
    }

    public void  setBalloonProgressBar (BalloonProgress bpb) {
        mBalloonProgress = bpb;
    }

    @Override
    public void onClick(View view) {
        if(!DownloadService.isQueued(mMessageId)) {
            mLbm = LocalBroadcastManager.getInstance(getContext());
            mLbm.registerReceiver(mDownloadReceiver, mDownloadFilter);
            mDownloadButton.setBackgroundResource(R.drawable.attachement_cancel);
            mProgressBar.setVisibility(VISIBLE);
            startDownload(mComponent);
        }
        else {
            mDownloadButton.setBackgroundResource(R.drawable.attachement_download);
            mLbm.unregisterReceiver(mDownloadReceiver);
            mProgressBar.setVisibility(GONE);
            stopDownload(mComponent);
        }
    }

    @Override
    public void setProgress(int progress, long messageId) {
    }

    @Override
    public void setVisible(int visibility) {
       mProgressBar.setVisibility(visibility);
    }

    @Override
    public void startDownload(AttachmentComponent attachment) {
        if (attachment != null && attachment.getFetchUrl() != null) {
            Intent i = new Intent(getContext(), DownloadService.class);
            i.setAction(DownloadService.ACTION_DOWNLOAD_URL);
            i.putExtra(CompositeMessage.MSG_ID, mMessageId);
            i.putExtra(CompositeMessage.MSG_SENDER, mMessageSender);
            i.putExtra(CompositeMessage.MSG_ENCRYPTED, attachment.getSecurityFlags() != Coder.SECURITY_CLEARTEXT);
            i.setData(Uri.parse(attachment.getFetchUrl()));
            getContext().startService(i);
        }
        else {
            // corrupted message :(
            Toast.makeText(getContext(), R.string.err_attachment_corrupted,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void stopDownload(AttachmentComponent attachment) {
        if (attachment != null && attachment.getFetchUrl() != null) {
            Intent i = new Intent(getContext(), DownloadService.class);
            i.setAction(DownloadService.ACTION_DOWNLOAD_ABORT);
            i.setData(Uri.parse(attachment.getFetchUrl()));
            getContext().startService(i);
        }
    }
}
