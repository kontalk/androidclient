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
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

import org.kontalk.data.Conversation;
import org.kontalk.ui.view.ConversationListItem;


class ConversationViewHolder extends RecyclerView.ViewHolder implements
        View.OnClickListener {

    private final ConversationListAdapter.OnItemClickListener mListener;

    ConversationViewHolder(ConversationListItem itemView,
        ConversationListAdapter.OnItemClickListener listener) {
        super(itemView);
        mListener = listener;
        itemView.setOnClickListener(this);
        //itemView.setOnLongClickListener(this);
    }

    void bindView(Context context, Conversation conversation) {
        if (conversation != null) {
            ((ConversationListItem) itemView).bind(context, conversation);
        }
    }

    void unbindView() {
        ((ConversationListItem) itemView).unbind();
    }

    @Override
    public void onClick(View v) {
        /*
        if (mListener != null) {
            mListener.onItemClick((ConversationListItem) itemView,
                getAdapterPosition());
        }
         */
    }

    public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
        return new ItemDetailsLookup.ItemDetails<Long>() {
            @Override
            public int getPosition() {
                return getAdapterPosition();
            }

            @Nullable
            @Override
            public Long getSelectionKey() {
                Conversation conv = ((ConversationListItem) itemView).getConversation();
                return conv != null ? conv.getThreadId() : null;
            }
        };
    }


}

