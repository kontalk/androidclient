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

package org.kontalk.data;

import android.arch.paging.DataSource;
import android.content.Context;
import android.os.Handler;


public class ConversationsDataSourceFactory extends DataSource.Factory<Integer, Conversation> {

    private final Context mContext;
    private final Handler mHandler;

    public ConversationsDataSourceFactory(Context context) {
        mContext = context.getApplicationContext();
        mHandler = new Handler();
    }

    @Override
    public DataSource<Integer, Conversation> create() {
        return new ConversationsDataSource(mContext, mHandler);
    }
}
