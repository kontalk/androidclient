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

package org.kontalk.ui.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;

import org.kontalk.data.Conversation;
import org.kontalk.ui.view.ConversationListItem;


class ConversationViewHolder extends RecyclerView.ViewHolder {

    ConversationViewHolder(ConversationListItem itemView) {
        super(itemView);
    }

    void bindView(Context context, Conversation conversation) {
        if (conversation != null) {
            ((ConversationListItem) itemView).bind(context, conversation);
        }
    }

    void unbindView() {
        ((ConversationListItem) itemView).unbind();
    }

}
