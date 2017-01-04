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


import android.net.Uri;

/**
 * Interface implemented by activities that can be parent of
 * {@link ComposeMessageFragment}.
 * @author Daniele Ricci
 */
interface ComposeMessageParent {

    /** Sets title and subtitle. Pass null to any of them to skip. */
    void setTitle(CharSequence title, CharSequence subtitle);

    /** Sets the subtitle in an updating status. */
    void setUpdatingSubtitle();

    /** Loads the given conversation, replacing the fragment as needed. */
    void loadConversation(long threadId, boolean creatingGroup);

    /** Loads the given conversation, replacing the fragment as needed. */
    void loadConversation(Uri threadUri);
}
