/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.util.MessageUtils;


/**
 * Credits fragment.
 * @author Daniele Ricci
 */
public class CreditsFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_credits, container, false);

        TextView coredevs = view.findViewById(R.id.coredevs);
        TextView contrib = view.findViewById(R.id.contrib);
        TextView gfx = view.findViewById(R.id.gfx);
        TextView translators = view.findViewById(R.id.translators);

        InputStream in = null;
        try {
            in = getResources().openRawResource(R.raw.credits);

            ByteArrayOutputStream bos = MessageUtils.readFully(in, 8192);
            JSONTokener reader = new JSONTokener(bos.toString());
            JSONObject root = (JSONObject) reader.nextValue();

            JSONArray devteam = root.getJSONArray("devteam");
            addCredits(coredevs, devteam);

            JSONArray contributors = root.getJSONArray("contributors");
            addCredits(contrib, contributors);

            JSONArray gfxteam = root.getJSONArray("gfx");
            addCredits(gfx, gfxteam);

            JSONArray trans = root.getJSONArray("translators");
            addCredits(translators, trans);
        }
        catch (Exception ignore) {
        }
        finally {
            try {
                if (in != null)
                    in.close();
            }
            catch (IOException ignore) {
            }
        }

        return view;
    }

    private void addCredits(TextView view, JSONArray list) throws JSONException {
        StringBuilder text = new StringBuilder();
        int c = list.length();
        for (int i = 0; i < c; i++) {
            JSONObject credit = list.getJSONObject(i);
            text.append(credit.getString("name"))
                .append("\n<")
                .append(credit.getString("email"))
                .append(">\n");
        }

        view.setText(text);
    }

}
