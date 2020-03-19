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

import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;

import org.kontalk.data.Conversation;


/**
 * Callback interface for conversations list fragments.
 * Mainly used to provide a common parent interface to {@link ConversationsFragment}.
 * @author Daniele Ricci
 */
public interface ConversationsCallback {

    void openConversation(Conversation conv);

    ActionMode startSupportActionMode(@NonNull ActionMode.Callback callback);

    void onDatabaseChanged();

}
