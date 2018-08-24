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

package org.kontalk.ui.model;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.paging.LivePagedListBuilder;
import android.arch.paging.PagedList;
import android.content.Context;
import android.support.annotation.UiThread;

import org.kontalk.data.Conversation;
import org.kontalk.data.ConversationsDataSourceFactory;


public class ConversationsViewModel extends ViewModel {

    private LiveData<PagedList<Conversation>> mData;

    @UiThread
    public void load(Context context) {
        PagedList.Config config = new PagedList.Config.Builder()
            .setPageSize(100)
            .setEnablePlaceholders(false)
            .build();
        mData = new LivePagedListBuilder<>(
            new ConversationsDataSourceFactory(context.getApplicationContext()),
            config)
            .build();
    }

    public LiveData<PagedList<Conversation>> getData() {
        return mData;
    }

}
