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

import org.jivesoftware.smack.util.StringUtils;

import android.arch.paging.PagedListAdapter;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.util.DiffUtil;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.kontalk.R;
import org.kontalk.data.Conversation;
import org.kontalk.ui.view.ConversationListItem;


public class ConversationListAdapter extends PagedListAdapter<Conversation, ConversationViewHolder> {
    private static final DiffUtil.ItemCallback<Conversation> sDiffCallback = new DiffUtil.ItemCallback<Conversation>() {
        @Override
        public boolean areItemsTheSame(Conversation oldItem, Conversation newItem) {
            return oldItem.getThreadId() == newItem.getThreadId();
        }

        @Override
        public boolean areContentsTheSame(Conversation oldItem, Conversation newItem) {
            // include any attribute that might change the state of the UI
            return oldItem.getThreadId() == newItem.getThreadId() &&
                oldItem.getStatus() == newItem.getStatus() &&
                oldItem.getDate() == newItem.getDate() &&
                oldItem.isSticky() == newItem.isSticky() &&
                oldItem.getUnreadCount() == newItem.getUnreadCount() &&
                oldItem.getRequestStatus() == newItem.getRequestStatus() &&
                StringUtils.nullSafeCharSequenceEquals(oldItem.getSubject(), newItem.getSubject()) &&
                StringUtils.nullSafeCharSequenceEquals(oldItem.getDraft(), newItem.getDraft());
        }
    };

    private final LayoutInflater mFactory;
    private final MultiSelector mMultiSelector;
    private final OnItemClickListener mListener;

    public ConversationListAdapter(Context context, MultiSelector multiSelector, OnItemClickListener listener) {
        super(sDiffCallback);
        mFactory = LayoutInflater.from(context);
        mMultiSelector = multiSelector;
        mListener = listener;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversationViewHolder((ConversationListItem) mFactory
            .inflate(R.layout.conversation_list_item, parent, false), mMultiSelector, mListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        holder.bindView(mFactory.getContext(), getItem(position));
    }

    @Override
    public void onViewRecycled(@NonNull ConversationViewHolder holder) {
        holder.unbindView();
    }

    public interface OnItemClickListener {
        void onItemClick(ConversationListItem item, int position);

        void onStartMultiselect();

        void onItemSelected(ConversationListItem item, int position);
    }

}
