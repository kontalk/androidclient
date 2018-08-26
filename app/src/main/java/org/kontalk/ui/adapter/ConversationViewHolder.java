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

import com.bignerdranch.android.multiselector.MultiSelector;

import android.content.Context;
import android.support.v4.content.res.ResourcesCompat;
import android.view.View;

import org.kontalk.R;
import org.kontalk.data.Conversation;
import org.kontalk.ui.view.ConversationListItem;


class ConversationViewHolder extends SwappingHolder implements
        View.OnClickListener, View.OnLongClickListener {

    private final MultiSelector mMultiSelector;
    private final ConversationListAdapter.OnItemClickListener mListener;

    ConversationViewHolder(ConversationListItem itemView, MultiSelector multiSelector,
            ConversationListAdapter.OnItemClickListener listener) {
        super(itemView, multiSelector);
        mMultiSelector = multiSelector;
        mListener = listener;
        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);
        // TODO this is not compatible with theme change (e.g. dark theme)
        setSelectionModeBackgroundDrawable(ResourcesCompat.getDrawable(itemView.getResources(),
            R.drawable.list_item_background, itemView.getContext().getTheme()));
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
        if (!mMultiSelector.tapSelection(this)) {
            if (mListener != null) {
                mListener.onItemClick((ConversationListItem) itemView,
                    getAdapterPosition());
            }
        }
        else {
            // multiselecting
            if (mListener != null) {
                mListener.onItemSelected((ConversationListItem) itemView,
                    getAdapterPosition());
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (!mMultiSelector.isSelectable()) {
            if (mListener != null) {
                mListener.onStartMultiselect();
            }
            mMultiSelector.setSelectable(true);
            mMultiSelector.setSelected(this, true);
            if (mListener != null) {
                mListener.onItemSelected((ConversationListItem) itemView,
                    getAdapterPosition());
            }
            return true;
        }
        return false;
    }
}
