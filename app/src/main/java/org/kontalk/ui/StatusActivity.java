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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.kontalk.R;
import org.kontalk.service.msgcenter.MessageCenterService;


/**
 * Status message activity.
 * TODO use popup activity on tablet
 * @author Daniele Ricci
 */
public class StatusActivity extends ToolbarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status_screen);

        setupToolbar(true, true);
    }

    public static void start(Activity context) {
        Intent intent = new Intent(context, StatusActivity.class);
        context.startActivityIfNeeded(intent, -1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // hold message center
        MessageCenterService.hold(this, true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release message center
        MessageCenterService.release(this);
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return true;
    }

}
