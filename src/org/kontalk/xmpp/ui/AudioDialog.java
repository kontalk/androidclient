package org.kontalk.xmpp.ui;

import org.kontalk.xmpp.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

public class AudioDialog extends AlertDialog {

    public AudioDialog(Context context) {
        super(context);
        init();
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v = inflater.inflate(R.layout.audio_dialog, null);
        setView(v);

        v.findViewById(R.id.imageView1).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //((ImageView) v).setImageResource();
                getButton(Dialog.BUTTON_NEUTRAL).setVisibility(View.VISIBLE);
                getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
            }
        });

        setButton(Dialog.BUTTON_NEUTRAL, "Play", (OnClickListener) null);
        setButton(Dialog.BUTTON_POSITIVE, "Send", (OnClickListener) null);
        setButton(Dialog.BUTTON_NEGATIVE, "Cancel", (OnClickListener) null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getButton(Dialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.GONE);
    }
}
