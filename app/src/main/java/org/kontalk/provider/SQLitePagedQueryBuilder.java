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

package org.kontalk.provider;

import android.database.sqlite.SQLiteQueryBuilder;


/**
 * A query builder for paged queries.
 * @author Daniele Ricci
 */
public class SQLitePagedQueryBuilder extends SQLiteQueryBuilder {

    private int mCount;
    private String mLastColumn;
    private int mLastValue;

    public void setPage(int count, String lastColumn, int lastValue) {
        mCount = count;
        mLastColumn = lastColumn;
        mLastValue = lastValue;
    }

    @Override
    public String buildQuery(String[] projectionIn, String selection, String groupBy, String having, String sortOrder, String limit) {
        if (mLastColumn != null) {
            // build the inner query with reverse order and row count
            // NOTE: passed limit is ignored!!

            StringBuilder newSelection = new StringBuilder();
            if (mLastValue > 0) {
                if (selection != null && selection.length() > 0) {
                    newSelection
                        .append('(')
                        .append(selection)
                        .append(") AND ");
                }

                newSelection
                    .append(mLastColumn)
                    .append(" < ")
                    .append(String.valueOf(mLastValue));
            }

            String query = super.buildQuery(projectionIn, newSelection.toString(), groupBy, having, mLastColumn + " DESC", String.valueOf(mCount));
            // wrap the query with the original sort order
            return "SELECT * FROM (" + query + ") ORDER BY " + sortOrder;
        }
        else {
            return super.buildQuery(projectionIn, selection, groupBy, having, sortOrder, limit);
        }
    }

}
