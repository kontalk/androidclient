/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import org.kontalk.R;


/**
 * A base class for toolbar-based activities.
 * @author Daniele Ricci
 */
public abstract class ToolbarActivity extends AppCompatActivity {

    protected Toolbar setupToolbar(boolean home) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (home)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        return toolbar;
    }
}
