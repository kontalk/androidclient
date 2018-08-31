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
import android.view.View;
import android.widget.TextView;

import org.kontalk.R;


class ConversationFooterViewHolder extends RecyclerView.ViewHolder implements
        View.OnClickListener {

    private final ConversationListAdapter.OnFooterClickListener mListener;

    ConversationFooterViewHolder(View itemView, ConversationListAdapter.OnFooterClickListener listener) {
        super(itemView);
        mListener = listener;
        if (listener != null) {
            itemView.setOnClickListener(this);
        }
    }

    void bindView(Context context, Integer archivedCount) {
        if (archivedCount != null && archivedCount > 0) {
            ((TextView) itemView).setText(context
                .getString(R.string.footer_archived_threads, archivedCount));
        }
        else {
            itemView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onClick(View v) {
        mListener.onFooterClick();
    }

}
