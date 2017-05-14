/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.view.ViewHelper;
import com.nineoldandroids.view.ViewPropertyAnimator;
import com.rockerhieu.emojicon.EmojiconsView;
import com.rockerhieu.emojicon.OnEmojiconClickedListener;
import com.rockerhieu.emojicon.emoji.Emojicon;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.message.AudioComponent;
import org.kontalk.ui.AudioDialog;
import org.kontalk.ui.ComposeMessage;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;


/**
 * The composer bar.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class ComposerBar extends RelativeLayout implements
        EmojiconsView.OnEmojiconBackspaceClickedListener, OnEmojiconClickedListener {
    private static final String TAG = ComposeMessage.TAG;

    private static final int MIN_RECORDING_TIME = 900;
    static final long MAX_RECORDING_TIME = TimeUnit.MINUTES.toMillis(2);
    private static final int AUDIO_RECORD_VIBRATION = 20;
    private static final int AUDIO_RECORD_ANIMATION = 300;

    private static final String MAX_RECORDING_TIME_TEXT = DateUtils
        .formatElapsedTime(MAX_RECORDING_TIME / 1000);

    private Context mContext;

    // for the text entry
    private boolean mSendEnabled = true;
    private EditText mTextEntry;
    private View mSendButton;
    private ComposerListener mListener;
    private TextWatcher mChatStateListener;

    private boolean mEnterSend;

    /** Used during audio recording to restore focus status of the text entry. */
    private boolean mTextEntryFocus;
    private boolean mComposeSent;

    // for Emoji drawer
    private ImageButton mEmojiButton;
    private EmojiconsView mEmojiView;
    private boolean mEmojiVisible;
    KeyboardAwareRelativeLayout mRootView;
    private WindowManager.LayoutParams mWindowLayoutParams;

    // for PTT message
    Handler mHandler;
    private Runnable mMediaPlayerUpdater;
    View mAudioButton;
    View mRecordLayout;
    View mSlideText;
    private float mDraggingX = -1;
    private float mDistMove;
    private boolean mIsRecordingAudio;
    private TextView mRecordText;
    private File mRecordFile;
    private MediaRecorder mRecord;
    long startTime;
    long elapsedTime;
    private boolean mCheckMove;
    private int mOrientation;
    private Vibrator mVibrator;
    // initialized in onCreate
    int mMoveThreshold;
    private int mMoveOffset;
    private int mMoveOffset2;

    public ComposerBar(Context context) {
        super(context);
        init(context);
    }

    public ComposerBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ComposerBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ComposerBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mHandler = new Handler();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTextEntry = (EditText) findViewById(R.id.text_editor);

        // enter key flag
        int inputTypeFlags;
        String enterKeyMode = Preferences.getEnterKeyMode(mContext);
        if ("newline".equals(enterKeyMode)) {
            inputTypeFlags = mTextEntry.getInputType() | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE;
        }
        else if ("newline_send".equals(enterKeyMode)) {
            inputTypeFlags = (mTextEntry.getInputType() & ~InputType.TYPE_TEXT_FLAG_MULTI_LINE) |
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE;
            mTextEntry.setImeOptions(EditorInfo.IME_ACTION_SEND);
            mTextEntry.setInputType(inputTypeFlags);
            mEnterSend = true;
        }
        else {
            inputTypeFlags = mTextEntry.getInputType() | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
        }

        mTextEntry.setInputType(inputTypeFlags);

        mTextEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // enable the send button if there is something to send
                boolean textPresent = s.length() > 0;
                if (mAudioButton != null) {
                    mAudioButton.setVisibility(textPresent ? View.INVISIBLE : View.VISIBLE);
                    mSendButton.setVisibility(textPresent ? View.VISIBLE : View.INVISIBLE);
                }
                // audio button should already be disabled if needed
                mSendButton.setEnabled(textPresent && mSendEnabled);

                if (mListener != null)
                    mListener.textChanged(s);

                // convert ascii to emojis if preference set
                /* FIXME doesn't work yet because of issues with Emojicon
                if (Preferences.getEmojiConverter(mContext)) {
                    mTextEntry.removeTextChangedListener(this);
                    MessageUtils.convertSmileys(s);
                    mTextEntry.addTextChangedListener(this);
                }
                */
            }
        });
        mTextEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND && mSendEnabled) {
                    if (!mEnterSend) {
                        InputMethodManager imm = (InputMethodManager) mContext
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getApplicationWindowToken(), 0);
                    }
                    submitSend();
                    return true;
                }
                return false;
            }
        });
        mChatStateListener = new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mSendEnabled && Preferences.getSendTyping(mContext)) {
                    // send typing notification if necessary
                    if (!mComposeSent && mListener.sendTyping()) {
                        mComposeSent = true;
                    }
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        mTextEntry.addTextChangedListener(mChatStateListener);

        mTextEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isEmojiVisible())
                    hideEmojiDrawer(false);
            }
        });
        mTextEntry.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && isEmojiVisible())
                    hideEmojiDrawer(false);
            }
        });

        mSendButton = findViewById(R.id.send_button);
        mSendButton.setEnabled(mTextEntry.length() > 0);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitSend();
            }
        });

        if (AudioDialog.isSupported(mContext)) {
            mAudioButton = findViewById(R.id.audio_send_button);

            if (mTextEntry.length() <= 0) {
                mSendButton.setVisibility(View.INVISIBLE);
                mAudioButton.setVisibility(View.VISIBLE);
            }

            mSlideText = findViewById(R.id.slide_text);
            mRecordText = (TextView) findViewById(R.id.recording_time);

            int screenWidth = SystemUtils.getDisplaySize(mContext).x;
            // position of "slide to cancel" label (actually left margin; screen dependent)
            mMoveThreshold = screenWidth/8;
            // these two are used to determine how much to drag in order to cancel recording
            mMoveOffset = (int) (screenWidth/4.5);
            mMoveOffset2 = (int) (screenWidth/7.5);

            mDistMove = mMoveOffset;

            mRecordLayout = findViewById(R.id.record_layout);

            mAudioButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        mOrientation = SystemUtils.getDisplayRotation(mContext);
                        mCheckMove = false;
                        mDraggingX = -1;
                        startRecording();
                        animateRecordFrame();
                        mAudioButton.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    else if ((motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) &&
                            !mCheckMove && mIsRecordingAudio) {
                        if (mOrientation == SystemUtils.getDisplayRotation(mContext)) {
                            mDraggingX = -1;
                            stopRecording(motionEvent.getAction() == MotionEvent.ACTION_UP);
                            animateRecordFrame();
                        }
                    }
                    else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && mIsRecordingAudio) {
                        float x = motionEvent.getX();
                        if (x < -mDistMove) {
                            mCheckMove = true;
                            stopRecording(false);
                            animateRecordFrame();
                        }
                        float currentX = ViewHelper.getX(mAudioButton);
                        x = x + currentX;
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mSlideText.getLayoutParams();
                        if (mDraggingX != -1) {
                            float dist = (x - mDraggingX);
                            params.leftMargin = mMoveThreshold + (int) dist;
                            mSlideText.setLayoutParams(params);
                            float alpha = 1.0f + dist / mDistMove;
                            if (alpha > 1) {
                                alpha = 1;
                            }
                            else if (alpha < 0) {
                                alpha = 0;
                            }
                            ViewHelper.setAlpha(mSlideText, alpha);
                        }
                        if (x <= currentX + mSlideText.getWidth() + mMoveThreshold) {
                            if (mDraggingX == -1) {
                                mDraggingX = x;
                                mDistMove = (mRecordLayout.getMeasuredWidth() - mSlideText.getMeasuredWidth() - mMoveOffset2) / 2.0f;
                                if (mDistMove <= 0) {
                                    mDistMove = mMoveOffset;
                                }
                                else if (mDistMove > mMoveOffset) {
                                    mDistMove = mMoveOffset;
                                }
                            }
                        }
                        if (params.leftMargin > mMoveThreshold) {
                            params.leftMargin = mMoveThreshold;
                            mSlideText.setLayoutParams(params);
                            ViewHelper.setAlpha(mSlideText, 1);
                            mDraggingX = -1;
                        }
                    }
                    view.onTouchEvent(motionEvent);
                    return true;
                }
            });
            mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        }

        mEmojiButton = (ImageButton) findViewById(R.id.emoji_button);
        mEmojiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleEmojiDrawer();
            }
        });

        doSetSendEnabled();
    }

    public void onPause() {
        if (mIsRecordingAudio) {
            abortRecording();
        }
    }

    public void setSendEnabled(boolean enabled) {
        mSendEnabled = enabled;
        doSetSendEnabled();
    }

    private void doSetSendEnabled() {
        mSendButton.setEnabled(mSendEnabled);
        if (mAudioButton != null)
            mAudioButton.setEnabled(mSendEnabled);
    }

    public void setRootView(View rootView) {
        if (!(rootView instanceof KeyboardAwareRelativeLayout)) {
            rootView = rootView.findViewById(R.id.root_view);
        }
        mRootView = (KeyboardAwareRelativeLayout) rootView;
        // this will handle closing of keyboard while emoji drawer is open
        mRootView.setOnKeyboardShownListener(new KeyboardAwareRelativeLayout.OnKeyboardShownListener() {
            @Override
            public void onKeyboardShown(boolean visible) {
                if (!visible && mRootView.getPaddingBottom() == 0 && isEmojiVisible()) {
                    hideEmojiDrawer(false);
                }
            }
        });
    }

    public void forceHideKeyboard() {
        InputMethodManager imm = (InputMethodManager) mContext
            .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mTextEntry.getApplicationWindowToken(), 0);
    }

    public void onSaveInstanceState(Bundle out) {
        // TODO
    }

    private void disableTextEntry() {
        mTextEntryFocus = mTextEntry.hasFocus();
        mTextEntry.setFocusable(false);
        mTextEntry.setFocusableInTouchMode(false);
    }

    private void enableTextEntry() {
        mTextEntry.setFocusable(true);
        mTextEntry.setFocusableInTouchMode(true);
        if (mTextEntryFocus)
            mTextEntry.requestFocus();
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        return mTextEntry.requestFocus(direction, previouslyFocusedRect);
    }

    void animateRecordFrame() {
        int screenWidth = SystemUtils.getDisplaySize(mContext).x;

        if (mIsRecordingAudio) {
            mRecordLayout.setVisibility(View.VISIBLE);
            setRecordText(0);

            FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) mSlideText.getLayoutParams();
            params.leftMargin = mMoveThreshold;
            mSlideText.setLayoutParams(params);
            ViewHelper.setAlpha(mSlideText, 1);
            ViewHelper.setX(mRecordLayout, screenWidth);
            ViewPropertyAnimator.animate(mRecordLayout)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        ViewHelper.setX(mRecordLayout, 0);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {
                    }
                })
                .setDuration(AUDIO_RECORD_ANIMATION)
                .translationX(0)
                .start();
        }
        else {
            ViewPropertyAnimator.animate(mRecordLayout)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        FrameLayout.LayoutParams params =
                            (FrameLayout.LayoutParams) mSlideText.getLayoutParams();
                        params.leftMargin = mMoveThreshold;
                        mSlideText.setLayoutParams(params);
                        ViewHelper.setAlpha(mSlideText, 1);
                        mRecordLayout.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {
                    }
                })
                .setDuration(AUDIO_RECORD_ANIMATION)
                .translationX(screenWidth)
                .start();
        }
    }

    private void startRecording() {
        // ask parent to stop all sounds
        mListener.stopAllSounds();

        try {
            mRecordFile = MediaStorage.getOutgoingAudioFile();
        }
        catch (IOException e) {
            Log.e(TAG, "error creating audio file", e);
            Toast.makeText(mContext, R.string.err_audio_record_writing,
                Toast.LENGTH_LONG).show();
            return;
        }

        mRecord = new MediaRecorder();
        try {
            mRecord.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecord.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecord.setOutputFile(mRecordFile.getAbsolutePath());
            mRecord.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mVibrator.vibrate(AUDIO_RECORD_VIBRATION);
            startTimer();
            mRecord.prepare();
            // Start recording
            mRecord.start();
            mIsRecordingAudio = true;
            lockScreen();
            disableTextEntry();
        }
        catch (IllegalStateException e) {
            Log.e(TAG, "error starting audio recording:", e);
        }
        catch (IOException e) {
            Log.e(TAG, "error writing on external storage:", e);
            Toast.makeText(mContext, R.string.err_audio_record_writing,
                Toast.LENGTH_LONG).show();
        }
        catch (RuntimeException e) {
            Log.e(TAG, "error starting audio recording:", e);
            Toast.makeText(mContext, R.string.err_audio_record,
                Toast.LENGTH_LONG).show();
        }
    }

    void stopRecording(boolean send) {
        mIsRecordingAudio = false;
        unlockScreen();
        enableTextEntry();

        mVibrator.vibrate(AUDIO_RECORD_VIBRATION);
        if (mMediaPlayerUpdater != null)
            mHandler.removeCallbacks(mMediaPlayerUpdater);

        boolean minDuration = (elapsedTime > MIN_RECORDING_TIME);
        boolean canSend = send && minDuration;
        // reset elapsed recording time
        elapsedTime = 0;

        try {
            if (mRecord != null) {
                mRecord.stop();
                if (canSend) {
                    mListener.sendBinaryMessage(Uri.fromFile(mRecordFile),
                        AudioDialog.DEFAULT_MIME, false, AudioComponent.class);
                }
                else if (send) {
                    Toast.makeText(mContext, R.string.hint_ptt,
                        Toast.LENGTH_LONG).show();
                }
            }
        }
        catch (IllegalStateException e) {
            Log.w(TAG, "error stopping recording", e);
            canSend = false;
        }
        catch (RuntimeException e) {
            if (send) {
                int msgId;
                if (!minDuration) {
                    msgId = R.string.hint_ptt;
                }
                else {
                    Log.w(TAG, "no audio data received", e);
                    msgId = R.string.err_audio_record_noaudio;
                }
                Toast.makeText(mContext, msgId, Toast.LENGTH_LONG).show();
            }
            canSend = false;
        }
        finally {
            if (mRecord != null) {
                mRecord.reset();
                mRecord.release();
            }

            if (!canSend && mRecordFile != null)
                mRecordFile.delete();
        }
    }

    /** Stops push-to-talk recording immediately. */
    private void abortRecording() {
        // this will prevent the touch event to be processed
        mCheckMove = true;
        // stop the actual recording without sending
        stopRecording(false);
        // hide the recording layout immediately
        mRecordLayout.setVisibility(View.GONE);
    }

    private void lockScreen() {
        int orientation = SystemUtils.getScreenOrientation((Activity) mContext);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
            orientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED;
        //noinspection ResourceType
        ((Activity) mContext).setRequestedOrientation(orientation);
        SystemUtils.acquireScreenOn((Activity) mContext);
    }

    private void unlockScreen() {
        ((Activity) mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        SystemUtils.releaseScreenOn((Activity) mContext);
    }

    private void startTimer() {
        startTime = SystemClock.uptimeMillis();
        mMediaPlayerUpdater = new Runnable() {
            @Override
            public void run() {
                elapsedTime = SystemClock.uptimeMillis() - startTime;
                setRecordText(elapsedTime);
                mHandler.postDelayed(this, 100);
                if (elapsedTime >= MAX_RECORDING_TIME) {
                    mAudioButton.setPressed(false);
                    stopRecording(true);
                    animateRecordFrame();
                }
            }
        };
        mHandler.postDelayed(mMediaPlayerUpdater, 100);
    }

    void setRecordText(long millis) {
        mRecordText.setText(mContext.getString(R.string.audio_duration_max,
            DateUtils.formatElapsedTime(millis / 1000),
            MAX_RECORDING_TIME_TEXT));
    }

    private void submitSend() {
        mTextEntry.removeTextChangedListener(mChatStateListener);
        // send message
        mListener.sendTextMessage(mTextEntry.getText().toString());
        // empty text
        mTextEntry.setText("");
        // hide softkeyboard
        hideSoftKeyboard();
        // reset compose sent flag
        mComposeSent = false;
        mTextEntry.addTextChangedListener(mChatStateListener);
        // revert to keyboard if emoji panel was open
        if (isEmojiVisible()) {
            hideEmojiDrawer();
        }
    }

    /** Returns true if typing message was sent. */
    public boolean isComposeSent() {
        return mComposeSent;
    }

    @Override
    public void onEmojiconBackspaceClicked(View v) {
        EmojiconsView.backspace(mTextEntry);
    }

    @Override
    public void onEmojiconClicked(Emojicon emojicon) {
        EmojiconsView.input(mTextEntry, emojicon);
    }

    public boolean isEmojiVisible() {
        return mEmojiVisible;
    }

    private void toggleEmojiDrawer() {
        // TODO animate drawer enter & exit

        if (isEmojiVisible()) {
            hideEmojiDrawer();
        }
        else {
            showEmojiDrawer();
        }
    }

    private void showEmojiDrawer() {
        int keyboardHeight = mRootView.getKeyboardHeight();

        mEmojiVisible = true;

        if (mEmojiView == null) {
            mEmojiView = (EmojiconsView) LayoutInflater
                .from(mContext).inflate(R.layout.emojicons, mRootView, false);
            mEmojiView.setId(R.id.emoji_drawer);
            mEmojiView.setOnEmojiconBackspaceClickedListener(this);
            mEmojiView.setOnEmojiconClickedListener(this);

            mWindowLayoutParams = new WindowManager.LayoutParams();
            mWindowLayoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            mWindowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
            mWindowLayoutParams.token = ((Activity) mContext).getWindow().getDecorView().getWindowToken();
            mWindowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }

        mWindowLayoutParams.height = keyboardHeight;
        mWindowLayoutParams.width = SystemUtils.getDisplaySize(mContext).x;

        WindowManager wm = (WindowManager) mContext.getSystemService(Activity.WINDOW_SERVICE);

        try {
            if (mEmojiView.getParent() != null) {
                wm.removeViewImmediate(mEmojiView);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "error removing emoji view", e);
        }

        try {
            wm.addView(mEmojiView, mWindowLayoutParams);
        }
        catch (Exception e) {
            Log.e(TAG, "error adding emoji view", e);
            return;
        }

        if (!mRootView.isKeyboardVisible()) {
            mRootView.setPadding(0, 0, 0, keyboardHeight);
            // TODO mEmojiButton.setImageResource(R.drawable.ic_msg_panel_hide);
        }

        mEmojiButton.setImageResource(R.drawable.ic_keyboard);
    }

    private void hideEmojiDrawer() {
        hideEmojiDrawer(true);
    }

    public void hideEmojiDrawer(boolean showKeyboard) {
        if (showKeyboard) {
            InputMethodManager input = (InputMethodManager) mContext
                .getSystemService(Context.INPUT_METHOD_SERVICE);
            input.showSoftInput(mTextEntry, 0);
        }

        if (mEmojiView != null && mEmojiView.getParent() != null) {
            WindowManager wm = (WindowManager) mContext
                .getSystemService(Context.WINDOW_SERVICE);
            wm.removeViewImmediate(mEmojiView);
        }

        mEmojiButton.setImageResource(R.drawable.ic_emoji);
        mRootView.setPadding(0, 0, 0, 0);
        mEmojiVisible = false;
    }

    public void setComposerListener(ComposerListener listener) {
        mListener = listener;
    }

    public void onKeyboardStateChanged(boolean isKeyboardOpen) {
        if (isKeyboardOpen) {
            mTextEntry.setFocusableInTouchMode(true);
            mTextEntry.setHint(R.string.hint_type_to_compose);
        }
        else {
            mTextEntry.setFocusableInTouchMode(false);
            mTextEntry.setHint(R.string.hint_open_kbd_to_compose);
        }
    }

    public void restoreText(CharSequence text) {
        if (text != null) {
            mTextEntry.removeTextChangedListener(mChatStateListener);

            // restore text (if any and only if user hasn't inserted text)
            if (mTextEntry.getText().length() == 0) {
                mTextEntry.setText(text);

                // move cursor to end
                mTextEntry.setSelection(mTextEntry.getText().length());
            }

            mTextEntry.addTextChangedListener(mChatStateListener);
        }
    }

    public void setText(CharSequence text) {
        mTextEntry.setText(text);
    }

    public CharSequence getText() {
        return mTextEntry.getText();
    }

    private void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) mContext
            .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mTextEntry.getWindowToken(),
            InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    public void resetCompose() {
        mComposeSent = false;
    }

    public void onDestroy() {
        if (mTextEntry != null) {
            mTextEntry.removeTextChangedListener(mChatStateListener);
            mTextEntry.setText("");
        }
        if (mIsRecordingAudio) {
            stopRecording(false);
        }
    }

}
