/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.xmpp.ui;

import org.kontalk.xmpp.R;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;


/**
 * About activity.
 * TODO this activity should be refactored:
 * - on phones: tabbed activity with fragments (about, donations, credit)
 * - on tablets: fragments opened directly from settings activity
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class AboutActivity extends ActionBarActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about_screen);

		setupActivity();

		try {
			PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);

			TextView txtVersion = (TextView) findViewById(R.id.version);
			txtVersion.setText(getString(R.string.about_version,
			    pInfo.versionName, pInfo.versionCode));

		} catch (NameNotFoundException e) {
		    // shouldn't happen
		}

	}

	private void setupActivity() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

	public void socialFacebook(View v) {
	    try {
	        // we try to first activate the Facebook app
	        getPackageManager().getPackageInfo("com.facebook.katana", 0);
	        startUrl(getString(R.string.facebook_profile));
	    } catch (Exception e) {
	        // no facebook (or error) - start the profile page URL
	        startUrl(getString(R.string.facebook_link));
	    }
	}

	public void socialGooglePlus(View v) {
	    startUrl(getString(R.string.googleplus_link));
	}

	public void socialIdentica(View v) {
	    startUrl(getString(R.string.identica_link));
	}

	public void socialTwitter(View v) {
	    startUrl(getString(R.string.twitter_link));
	}

	public void startUrl(String url) {
	    Intent link=new Intent(Intent.ACTION_VIEW, Uri.parse(url));
	    startActivity(link);
	}

}
