package org.kontalk.ui.view;

import java.io.File;
import java.io.IOException;

import com.rockerhieu.emojicon.EmojiconsView;
import com.rockerhieu.emojicon.OnEmojiconClickedListener;
import com.rockerhieu.emojicon.emoji.Emojicon;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
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
import android.util.Log;
import android.util.TypedValue;
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
    private static final int MAX_RECORDING_TIME = 60000;
    private static final int AUDIO_RECORD_VIBRATION = 20;
    private static final int AUDIO_RECORD_ANIMATION = 300;

    private Context mContext;

    private EditText mTextEntry;
    private View mSendButton;

    private boolean mComposeSent;

    private ComposerListener mListener;
    private TextWatcher mChatStateListener;
    private ImageButton mEmojiButton;
    private EmojiconsView mEmojiView;
    private boolean mEmojiVisible;
    private KeyboardAwareRelativeLayout mRootView;
    private WindowManager.LayoutParams mWindowLayoutParams;

    // for PTT message
    private Handler mHandler;
    private Runnable mMediaPlayerUpdater;
    private View mAudioButton;
    private View mRecordLayout;
    private View mSlideText;
    private float mDraggingX = -1;
    private float mDistMove;
    private boolean mIsRecordingAudio;
    private TextView mRecordText;
    private File mRecordFile;
    private MediaRecorder mRecord;
    private long startTime = 0L;
    private long elapsedTime = 0L;
    private boolean mCheckMove;
    private int mOrientation;
    private Vibrator mVibrator;
    // initialized in onCreate
    private int mMoveThreshold;
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
                if (s.length() > 0) {
                    mAudioButton.setVisibility(View.INVISIBLE);
                    mSendButton.setVisibility(View.VISIBLE);
                }
                else if (s.length() <= 0) {
                    mSendButton.setVisibility(View.INVISIBLE);
                    mAudioButton.setVisibility(View.VISIBLE);
                }
                mSendButton.setEnabled(s.length() > 0);
            }
        });
        mTextEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    InputMethodManager imm = (InputMethodManager) mContext
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getApplicationWindowToken(), 0);
                    submitSend();
                    return true;
                }
                return false;
            }
        });
        mChatStateListener = new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (Preferences.getSendTyping(mContext)) {
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
        mAudioButton = findViewById(R.id.audio_send_button);

        if (mTextEntry.length() <= 0)
            mSendButton.setVisibility(View.INVISIBLE);

        mSlideText = findViewById(R.id.slide_text);
        mRecordText = (TextView) findViewById(R.id.recording_time);

        // FIXME remove these hard-coded values before merging
        Resources r = getResources();
        mMoveThreshold = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, r.getDisplayMetrics());
        mMoveOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, r.getDisplayMetrics());
        mDistMove = mMoveOffset;
        mMoveOffset2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, r.getDisplayMetrics());

        mRecordLayout = findViewById(R.id.record_layout);

        mAudioButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    mOrientation = SystemUtils.getDisplayRotation(mContext);
                    mCheckMove = false;
                    mDraggingX = -1;
                    mIsRecordingAudio = true;
                    startRecording();
                    animateRecordFrame();
                    mAudioButton.getParent().requestDisallowInterceptTouchEvent(true);
                }
                else if ((motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) && !mCheckMove) {
                    if (mOrientation == SystemUtils.getDisplayRotation(mContext)) {
                        mDraggingX = -1;
                        stopRecording(true);
                        mIsRecordingAudio = false;
                        animateRecordFrame();
                    }
                }
                else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && mIsRecordingAudio) {
                    float x = motionEvent.getX();
                    if (x < -mDistMove) {
                        mCheckMove = true;
                        mIsRecordingAudio = false;
                        stopRecording(false);
                        animateRecordFrame();
                    }
                    if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.HONEYCOMB_MR2) {
                        x = x + mAudioButton.getX();
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
                            mSlideText.setAlpha(alpha);
                        }
                        if (x <= mSlideText.getX() + mSlideText.getWidth() + mMoveThreshold) {
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
                            mSlideText.setAlpha(1);
                            mDraggingX = -1;
                        }
                    }
                }
                view.onTouchEvent(motionEvent);
                return true;
            }
        });
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        mSendButton.setEnabled(mTextEntry.length() > 0);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitSend();
            }
        });

        mEmojiButton = (ImageButton) findViewById(R.id.emoji_button);
        mEmojiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleEmojiDrawer();
            }
        });
    }

    public void setRootView(View rootView) {
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

    public void onSaveInstanceState(Bundle out) {
        // TODO
    }

    @SuppressLint("NewApi")
    private void animateRecordFrame() {
        int screenWidth = SystemUtils.getDisplaySize(mContext).x;
        boolean supportsAnimation = (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2);

        if (mIsRecordingAudio) {
            mRecordLayout.setVisibility(View.VISIBLE);
            mRecordText.setText(DateUtils.formatElapsedTime(0));

            if (supportsAnimation) {
                FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) mSlideText.getLayoutParams();
                params.leftMargin = mMoveThreshold;
                mSlideText.setLayoutParams(params);
                mSlideText.setAlpha(1);
                mRecordLayout.setX(screenWidth);
                mRecordLayout.animate()
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                        }

                        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mRecordLayout.setX(0);
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
        }
        else {
            if (supportsAnimation) {
                mRecordLayout.animate()
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                        }

                        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            FrameLayout.LayoutParams params =
                                (FrameLayout.LayoutParams) mSlideText.getLayoutParams();
                            params.leftMargin = mMoveThreshold;
                            mSlideText.setLayoutParams(params);
                            mSlideText.setAlpha(1);
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
            else {
                mRecordLayout.setVisibility(View.GONE);
            }
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
        mRecord.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecord.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecord.setOutputFile(mRecordFile.getAbsolutePath());
        mRecord.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mVibrator.vibrate(AUDIO_RECORD_VIBRATION);
            startTimer();
            mRecord.prepare();
            // Start recording
            mRecord.start();
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

    private void stopRecording(boolean send) {
        mVibrator.vibrate(AUDIO_RECORD_VIBRATION);
        if (mMediaPlayerUpdater != null)
            mHandler.removeCallbacks(mMediaPlayerUpdater);

        boolean canSend = send && (elapsedTime > MIN_RECORDING_TIME);

        try {
            if (mRecord != null) {
                mRecord.stop();
                mRecord.reset();
                mRecord.release();
                if (canSend) {
                    mListener.sendBinaryMessage(Uri.fromFile(mRecordFile),
                        AudioDialog.DEFAULT_MIME, true, AudioComponent.class);
                }
            }
        }
        catch (IllegalStateException e) {
            Log.w(TAG, "error stopping recording", e);
            canSend = false;
        }
        catch (RuntimeException e) {
            Log.w(TAG, "no audio data received", e);
            canSend = false;
            Toast.makeText(mContext, R.string.err_audio_record,
                Toast.LENGTH_LONG).show();
        }
        finally {
            if (!canSend)
                mRecordFile.delete();
        }
    }

    private void startTimer() {
        startTime = SystemClock.uptimeMillis();
        mMediaPlayerUpdater = new Runnable() {
            @Override
            public void run() {
                elapsedTime = SystemClock.uptimeMillis() - startTime;
                mRecordText.setText(DateUtils.formatElapsedTime(elapsedTime / 1000));
                mHandler.postDelayed(this, 100);
                if (elapsedTime >= MAX_RECORDING_TIME) {
                    mIsRecordingAudio = false;
                    animateRecordFrame();
                    mAudioButton.setPressed(false);
                    stopRecording(true);
                }
            }
        };
        mHandler.postDelayed(mMediaPlayerUpdater, 100);
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
            mWindowLayoutParams.token = ((Activity)mContext).getWindow().getDecorView().getWindowToken();
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

        mEmojiButton.setImageResource(R.drawable.ic_keyboard_dark);
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

        mEmojiButton.setImageResource(R.drawable.ic_emoji_dark);
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
    }

}
