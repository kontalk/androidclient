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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import org.kontalk.R;
import org.kontalk.service.msgcenter.MessageCenterService;


/**
 * Contact information activity.
 * @author Daniele Ricci
 */
public class ContactInfoActivity extends ToolbarActivity implements ContactInfoFragment.ContactInfoParent {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contact_info_screen);

        setupToolbar(true, false);

        if (savedInstanceState == null) {
            Intent i = getIntent();
            String userId = i.getStringExtra("user");
            Fragment f = ContactInfoFragment.newInstance(userId);
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment, f)
                .commit();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release message center
        MessageCenterService.release(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // hold message center
        MessageCenterService.hold(this, true);
    }

    /** Not used. */
    @Override
    protected boolean isNormalUpNavigation() {
        return false;
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

    @Override
    public void finish() {
        super.finish();
        if (!(this instanceof ContactInfoDialog))
            overridePendingTransition(R.anim.stay, R.anim.slide_down);
    }

    public static void start(Activity context, Fragment parent, String userId, int requestCode) {
        Intent intent = new Intent(context, ContactInfoActivity.class);
        intent.putExtra("user", userId);
        parent.startActivityForResult(intent, requestCode);
        context.overridePendingTransition(R.anim.slide_up, R.anim.stay);
    }

    @Override
    public void dismiss() {
        finish();
    }

}
