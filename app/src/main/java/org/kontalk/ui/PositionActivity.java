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

import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.kontalk.R;
import org.kontalk.position.PositionManager;

/**
 * Location Activity
 *
 * @author andreacappelli
 */

public class PositionActivity extends ToolbarActivity {

    public static final String EXTRA_USERID = "org.kontalk.location.USERID";
    public static final String EXTRA_USERPOSITION = "org.kontalk.location.USERPOSITION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        setupToolbar(true, true);

        if (savedInstanceState == null) {
            Bundle bundle = new Bundle();
            bundle.putString(EXTRA_USERID, getIntent().getStringExtra(EXTRA_USERID));
            bundle.putSerializable(EXTRA_USERPOSITION, getIntent().getSerializableExtra(EXTRA_USERPOSITION));

            Fragment fragment = getIntent().getSerializableExtra(EXTRA_USERPOSITION) == null ?
                PositionManager.getSendPositionFragment(this) :
                PositionManager.getPositionFragment(this);

            if (fragment == null)
                finish();

            fragment.setArguments(bundle);

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.container, fragment, fragment.getClass().getName()).commit();
        }
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return true;
    }
}
