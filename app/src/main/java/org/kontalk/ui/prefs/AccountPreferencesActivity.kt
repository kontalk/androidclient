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

package org.kontalk.ui.prefs

import android.os.Bundle
import androidx.fragment.app.Fragment

import org.kontalk.R


/**
 * Special preferences activity invoked from an account screen in system settings.
 * @author Daniele Ricci
 */
class AccountPreferencesActivity : BasePreferencesActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.preferences_screen)
        setupToolbar(true, true)

        if (savedInstanceState == null) {
            val fragment: Fragment = AccountFragment()
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, fragment)
                    .commit()
        }
    }

    override fun isNormalUpNavigation(): Boolean {
        return true
    }

}
