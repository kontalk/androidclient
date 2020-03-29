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

package org.kontalk.util

import android.view.View
import androidx.fragment.app.Fragment
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import com.github.clans.fab.FloatingActionMenu
import org.kontalk.R
import org.kontalk.ui.ComposeMessageFragment
import org.kontalk.ui.ConversationsFragment


class Showcase {

    private open class Hint(open val key: String, open val view: Int, open val title: Int, open val message: Int)
    private class FloatingActionMenuHint(override val key: String, override val view: Int, override val title: Int, override val message: Int):
            Hint(key, view, title, message)
    private class ViewHint(override val key: String, override val view: Int, override val title: Int, override val message: Int):
            Hint(key, view, title, message)

    companion object {

        private val HINTS: MutableMap<String, Array<out Hint>> = HashMap()

        init {
            HINTS[ConversationsFragment::class.java.name] = arrayOf(
                    FloatingActionMenuHint("fab", R.id.action,
                            R.string.showcase_conversations_compose_title,
                            R.string.showcase_conversations_compose_message)
            )

            HINTS[ComposeMessageFragment::class.java.name] = arrayOf(
                    ViewHint("attach", R.id.attach_button,
                            R.string.showcase_composer_attach_title,
                            R.string.showcase_composer_attach_message)
            )
        }

        private fun getPreferenceKey(parent: String, hint: Hint): String {
            return "showcase_${parent}_${hint.key}"
        }

        /**
         * Checks if the given hint was already showed by looking in preferences.
         */
        private fun isHintShowed(parent: String, hint: Hint): Boolean {
            return Preferences.getShowcaseShowed(parent, hint.key)
        }

        private fun setHintShowed(parent: String, hint: Hint): Boolean {
            return Preferences.setShowcaseShowed(parent, hint.key, true)
        }

        // TODO to be used from a preference
        fun resetAllHints() {
            HINTS.forEach { hints ->
                hints.value.forEach { hint ->
                    Preferences.setShowcaseShowed(hints.key, hint.key, false)
                }
            }
        }

        /**
         * Show the next proper showcase in the given fragment.
         * Call this from {@link Fragment#onResume()} on {@link Fragment#onStart()}
         * @param fragment a fragment that was just opened
         */
        @JvmStatic
        fun showNextHint(fragment: Fragment) {
            val parent = fragment::class.java.name
            HINTS[parent]?.let { hints ->
                val nextHint = hints.find { !isHintShowed(parent, it) }

                nextHint?.let { hint ->
                    val view: View = when (hint) {
                        is FloatingActionMenuHint -> {
                            val fab = fragment.view!!.findViewById<FloatingActionMenu>(hint.view)
                            fab.menuIconView
                        }
                        is ViewHint -> {
                            fragment.view!!.findViewById(hint.view)
                        }
                        else -> {
                            throw IllegalArgumentException("no view found to showcase")
                        }
                    }

                    TapTargetView.showFor(fragment.activity, TapTarget
                            .forView(view, fragment.getString(hint.title), fragment.getString(hint.message))
                            .transparentTarget(true)
                            .cancelable(true)
                            // TODO other settings
                            .targetRadius(60)
                    )
                    setHintShowed(parent, hint)
                }
            }
        }

    }

}
