/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
import org.kontalk.billing.IabHelper;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;


/**
 * About activity.
 * TODO this activity should be refactored:
 * - on phones: tabbed activity with fragments (about, donations, credit)
 * - on tablets: fragments opened directly from settings activity
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class AboutActivity extends ActionBarActivity {

    public static final String ACTION_DONATION = "org.kontalk.DONATION";
    public static final String ACTION_CREDITS = "org.kontalk.CREDITS";

    private static final int ABOUT_ABOUT = 0;
    private static final int ABOUT_DONATION = 1;
    private static final int ABOUT_CREDITS = 2;

    private static final int NUM_ITEMS = 3;

    private AboutPagerAdapter mAdapter;
    private ViewPager mPager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about_screen);

		mAdapter = new AboutPagerAdapter(getSupportFragmentManager());

		mPager = (ViewPager) findViewById(R.id.pager);
		mPager.setAdapter(mAdapter);
		mPager.setOnPageChangeListener(
		    new ViewPager.OnPageChangeListener() {

                @Override
                public void onPageSelected(int position) {
                    getSupportActionBar().setSelectedNavigationItem(position);
                }

                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                }

                @Override
                public void onPageScrollStateChanged(int position) {
                }

            }
		);

        setupActivity();

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {

                if (ACTION_DONATION.equals(action))
                    mPager.setCurrentItem(ABOUT_DONATION, true);

                else if (ACTION_CREDITS.equals(action))
                    mPager.setCurrentItem(ABOUT_CREDITS, true);
            }
        }
	}

	private void setupActivity() {
        ActionBar bar = getSupportActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        ActionBar.TabListener listener = new ActionBar.TabListener() {

            @Override
            public void onTabSelected(Tab tab, FragmentTransaction ft) {
                mPager.setCurrentItem(tab.getPosition(), true);
            }

            @Override
            public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            }

            @Override
            public void onTabReselected(Tab tab, FragmentTransaction ft) {
            }

        };

        bar.addTab(bar.newTab()
            .setText(R.string.title_about)
            .setTabListener(listener));
        bar.addTab(bar.newTab()
            .setText(R.string.title_donation)
            .setTabListener(listener));
        bar.addTab(bar.newTab()
            .setText(R.string.title_credits)
            .setTabListener(listener));
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
		IabHelper iabHelper = fragment.getIabHelper();

        if (iabHelper == null || !iabHelper.handleActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data);
	}

	private static class AboutPagerAdapter extends FragmentPagerAdapter {

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

        public DonationFragment getDonationFragment() {
			return mDonationFragment;
		}

	}

}
