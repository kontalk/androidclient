/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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
import org.kontalk.util.SystemUtils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;


/**
 * About fragment.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class AboutFragment extends Fragment implements OnClickListener {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_about, container, false);

        Context parent = getActivity();
        if (parent != null) {
            TextView txtVersion = view.findViewById(R.id.version);
            txtVersion.setText(SystemUtils.getVersionFullName(parent));
        }

        view.findViewById(R.id.button_twitter).setOnClickListener(this);
        view.findViewById(R.id.button_googleplus).setOnClickListener(this);
        view.findViewById(R.id.button_facebook).setOnClickListener(this);
        view.findViewById(R.id.button_identica).setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_twitter:
                socialTwitter();
                break;

            case R.id.button_googleplus:
                socialGooglePlus();
                break;

            case R.id.button_facebook:
                socialFacebook();
                break;

            case R.id.button_identica:
                socialIdentica();
                break;
        }
    }

    private void socialFacebook() {
        try {
            // we try to first activate the Facebook app
            getActivity().getPackageManager().getPackageInfo("com.facebook.katana", 0);
            startUrl(getString(R.string.facebook_profile));
        }
        catch (Exception e) {
            // no facebook (or error) - start the profile page URL
            startUrl(getString(R.string.facebook_link));
        }
    }

    private void socialGooglePlus() {
        startUrl(getString(R.string.googleplus_link));
    }

    private void socialIdentica() {
        startUrl(getString(R.string.identica_link));
    }

    private void socialTwitter() {
        startUrl(getString(R.string.twitter_link));
    }

    private void startUrl(String url) {
        Intent link = SystemUtils.externalIntent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(link);
    }

}
