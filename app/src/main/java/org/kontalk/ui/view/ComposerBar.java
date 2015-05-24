package org.kontalk.ui.view;

import java.io.File;
import java.io.IOException;

import org.jivesoftware.smackx.chatstates.ChatState;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.message.AudioComponent;
import org.kontalk.service.msgcenter.MessageCenterService;
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
public class ComposerBar extends RelativeLayout {
    private static final String TAG = ComposeMessage.TAG;

    private Context mContext;

    private EditText mTextEntry;
    private View mSendButton;

    private ComposerListener mListener;
    private TextWatcher mChatStateListener;
    private ImageButton mEmojiButton;

    // for PTT message
    private View mAudioButton;
    private View mRecordLayout;
    private View mSlideText;
    private float mDraggingX = -1;
    private float mDistMove;
    private boolean mCheckRecordingAudio = false;
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
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSlideText = findViewById(R.id.slide_text);
        mRecordText = (TextView) findViewById(R.id.recording_time);

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
                    if (!mComposeSent && mAvailableResources.size() > 0) {
                        MessageCenterService.sendChatState(mContext, mUserJID, ChatState.composing);
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
                    Log.e(TAG, "Start Record");
                    mCheckMove = false;
                    mDraggingX = -1;
                    mCheckRecordingAudio = true;
                    startRecording();
                    animateRecordFrame();
                    mAudioButton.getParent().requestDisallowInterceptTouchEvent(true);
                }
                else if ((motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) && !mCheckMove) {
                    if (mOrientation == SystemUtils.getDisplayRotation(mContext)) {
                        Log.e(TAG, "Send File");
                        mDraggingX = -1;
                        stopRecording(true);
                        mCheckRecordingAudio = false;
                        animateRecordFrame();
                    }
                }
                else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && mCheckRecordingAudio) {
                    float x = motionEvent.getX();
                    if (x < -mDistMove) {
                        Log.e(TAG, "Delete File");
                        mCheckMove = true;
                        mCheckRecordingAudio = false;
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
                            } else if (alpha < 0) {
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

    @SuppressLint("NewApi")
    private void animateRecordFrame() {
        int screenWidth = SystemUtils.getDisplaySize(mContext).x;
        boolean supportsAnimation = (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2);

        if (mCheckRecordingAudio) {
            mRecordLayout.setVisibility(View.VISIBLE);
            mRecordText.setText("00:00");

            if (supportsAnimation) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)mSlideText.getLayoutParams();
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
                    .setDuration(300)
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
                    .setDuration(300)
                    .translationX(screenWidth)
                    .start();
            }
            else {
                mRecordLayout.setVisibility(View.GONE);
            }
        }
    }

    private void startRecording() {
        if (mPlayer != null)
            resetAudio(mAudioControl);
        try {
            mRecordFile = MediaStorage.getOutgoingAudioFile();
        }
        catch (IOException e) {
            Log.e(TAG, "file error: ", e);
        }
        mRecord = new MediaRecorder();
        mRecord.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecord.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecord.setOutputFile(mRecordFile.getAbsolutePath());
        mRecord.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mVibrator.vibrate(20);
            startTimer();
            mRecord.prepare();
            // Start recording
            mRecord.start();
        }
        catch (IllegalStateException e) {
            Log.e (TAG, "error starting audio recording:", e);
        }
        catch (IOException e) {
            Log.e(TAG, "error writing on external storage:", e);
            new AlertDialog.Builder(mContext)
                .setMessage(R.string.err_audio_record_writing)
                .setNegativeButton(mContext.getString(android.R.string.ok), (DialogInterface.OnClickListener) null)
                .show();
        }
        catch (RuntimeException e) {
            Log.e(TAG, "error starting audio recording:", e);
            new AlertDialog.Builder(mContext.getApplicationContext())
                .setMessage(R.string.err_audio_record)
                .setNegativeButton(mContext.getString(android.R.string.ok), (DialogInterface.OnClickListener) null)
                .show();
        }
    }

    private void stopRecording(boolean send) {
        mVibrator.vibrate(20);
        mHandler.removeCallbacks(mMediaPlayerUpdater);
        try {
            mRecord.stop();
            mRecord.reset();
            mRecord.release();
            if (send && (elapsedTime > 900)) {
                sendBinaryMessage(Uri.fromFile(mRecordFile), AudioDialog.DEFAULT_MIME, true, AudioComponent.class);
            } else {
                Log.e(TAG,"File Cancellato");
                mRecordFile.delete();
            }
        }
        catch (IllegalStateException e) {
            //ignore
        }
        catch (RuntimeException e) {
            //ignore
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
                if (elapsedTime >= 60000) {
                    mCheckRecordingAudio = false;
                    animateRecordFrame();
                    mAudioButton.setPressed(false);
                    stopRecording(true);
                }
            }
        };
        mHandler.postDelayed(mMediaPlayerUpdater, 100);
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

    public void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) mContext
            .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mTextEntry.getWindowToken(),
            InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    public void onDestroy() {
        if (mTextEntry != null) {
            mTextEntry.removeTextChangedListener(mChatStateListener);
            mTextEntry.setText("");
        }
    }

}
