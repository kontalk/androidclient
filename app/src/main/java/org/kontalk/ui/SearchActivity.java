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

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;

import org.kontalk.R;


/**
 * Activity wrapper for {@link SearchFragment}.
 * @author Daniele Ricci
 */
public class SearchActivity extends ToolbarActivity {
    public static final String TAG = SearchActivity.class.getSimpleName();

    private SearchFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_list_screen);

        setupToolbar(true, true);

        mFragment = (SearchFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_search_list);

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            setTitle(getResources().getString(R.string.title_search, query));

            mFragment.setQuery(query);
        }
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return true;
    }

}
