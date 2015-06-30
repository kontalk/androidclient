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

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;

import org.kontalk.R;
import org.kontalk.billing.IBillingService;


/**
 * About activity.
 * TODO this activity should be refactored:
 * - on phones: tabbed activity with fragments (about, donations, credit)
 * - on tablets: fragments opened directly from settings activity
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class AboutActivity extends ToolbarActivity {

    public static final String ACTION_DONATION = "org.kontalk.DONATION";
    public static final String ACTION_CREDITS = "org.kontalk.CREDITS";

    private static final int ABOUT_ABOUT = 0;
    private static final int ABOUT_DONATION = 1;
    private static final int ABOUT_CREDITS = 2;

    private static final int NUM_ITEMS = 3;

    private AboutPagerAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_screen);

        setupToolbar(true);

        mAdapter = new AboutPagerAdapter(getSupportFragmentManager());

        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(mAdapter);

        TabLayout tabs = (TabLayout) findViewById(R.id.sliding_tabs);
        tabs.setupWithViewPager(pager);

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {

                if (ACTION_DONATION.equals(action))
                    pager.setCurrentItem(ABOUT_DONATION, true);

                else if (ACTION_CREDITS.equals(action))
                    pager.setCurrentItem(ABOUT_CREDITS, true);
            }
        }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        DonationFragment fragment = mAdapter.getDonationFragment();
        IBillingService service = fragment.getBillingService();

        if (service == null || !service.handleActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data);
    }

    private class AboutPagerAdapter extends FragmentPagerAdapter {

        // this is for IabHelper
        private DonationFragment mDonationFragment;

        public AboutPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case ABOUT_ABOUT:
                    return new AboutFragment();

                case ABOUT_DONATION:
                    mDonationFragment = new DonationFragment();
                    return mDonationFragment;

                case ABOUT_CREDITS:
                    return new CreditsFragment();
            }

            // shouldn't happen, but just in case
            return new AboutFragment();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case ABOUT_ABOUT:
                    return getText(R.string.title_about);

                case ABOUT_DONATION:
                    return getText(R.string.title_donation);

                case ABOUT_CREDITS:
                    return getText(R.string.title_credits);
            }

            // shouldn't happen, but just in case
            return super.getPageTitle(position);
        }

        public DonationFragment getDonationFragment() {
            return mDonationFragment;
        }

    }

}
