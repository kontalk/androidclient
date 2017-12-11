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

import android.content.Intent;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import org.kontalk.R;


/**
 * A base class for toolbar-based activities.
 * @author Daniele Ricci
 */
public abstract class ToolbarActivity extends AppCompatActivity {

    private boolean mUseUpNavigation;

    /**
     * Setup the toolbar.
     * @param home whether to enable {@link android.support.v7.app.ActionBar#setDisplayHomeAsUpEnabled}.
     * @param useUpNavigation whether to enable up navigation behavior.
     * @return the toolbar
     */
    protected Toolbar setupToolbar(boolean home, boolean useUpNavigation) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mUseUpNavigation = useUpNavigation;
        if (home)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        return toolbar;
    }

    /**
     * @see <a href="https://developer.android.com/training/implementing-navigation/ancestral.html">Providing Up Navigation</a>
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mUseUpNavigation) {
            switch (item.getItemId()) {
                case android.R.id.home:
                    navigateUp();
                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    public void navigateUp() {
        if (isNormalUpNavigation()) {
            NavUtils.navigateUpFromSameTask(this);
        }
        else {
            Intent upIntent = NavUtils.getParentActivityIntent(this);
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is NOT part of this app's task, so create a new task
                // when navigating up, with a synthesized back stack.
                TaskStackBuilder.create(this)
                    // Add all of this activity's parents to the back stack
                    .addNextIntentWithParentStack(upIntent)
                    // Navigate up to the closest parent
                    .startActivities();
                finish();
            }
            else {
                // This activity is part of this app's task, so simply
                // navigate up to the logical parent activity.
                NavUtils.navigateUpTo(this, upIntent);
            }
        }
    }

    /**
     * Subclasses should return true if we should use
     * {@link android.support.v4.app.NavUtils#navigateUpFromSameTask(android.app.Activity)} for
     * navigating up or false if we should create a new task.
     */
    protected abstract boolean isNormalUpNavigation();

}
