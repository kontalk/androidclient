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

package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.util.Preferences;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;


/**
 * Status message fragment.
 * @author Daniele Ricci
 */
public class StatusFragment extends ListFragment implements View.OnClickListener {

    private EditText mStatus;
    private CursorAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = new SimpleCursorAdapter(getActivity(),
            android.R.layout.simple_list_item_1, null,
            new String[] { "status" }, new int[] { android.R.id.text1 });
        setListAdapter(mAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.status_fragment, container, false);

        mStatus = view.findViewById(android.R.id.input);
        // TODO retrieve current status from server?
        mStatus.setText(Preferences.getStatusMessage());

        view.findViewById(R.id.button_ok).setOnClickListener(this);
        view.findViewById(R.id.button_cancel).setOnClickListener(this);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        // TODO async query
        Cursor c = Preferences.getRecentStatusMessages(getActivity());
        mAdapter.changeCursor(c);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mAdapter != null)
            mAdapter.changeCursor(null);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Cursor c = (Cursor) mAdapter.getItem(position);
        // 0 = _id, 1 = status
        finish(c.getString(1));
    }

    private void finish(String text) {
        Activity parent = getActivity();

        if (parent != null) {
            if (text.trim().length() <= 0)
                text = text.trim();
            Preferences.setStatusMessage(text);
            Preferences.addRecentStatusMessage(parent, text);

            // start the message center to push the status message
            MessageCenterService.updateStatus(parent);

            parent.finish();
        }
    }

    public void onStatusOk() {
        String text = mStatus.getText().toString();
        finish(text);
    }

    public void onStatusCancel() {
        getActivity().finish();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_ok:
                onStatusOk();
                break;

            case R.id.button_cancel:
                onStatusCancel();
                break;
        }
    }
}
