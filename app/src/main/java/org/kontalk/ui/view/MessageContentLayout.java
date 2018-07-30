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

package org.kontalk.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;


/**
 * Linear layout for holding instances of message content.
 * @author Daniele Ricci
 */
public class MessageContentLayout extends LinearLayout {

    public MessageContentLayout(Context context) {
        super(context);
    }

    public MessageContentLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private int getPositionFromPriority(MessageContentView<?> view) {
        int prio = view.getPriority();
        int count = getChildCount();

        int position = count;

        // search for the right position based on priority
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child instanceof MessageContentView) {
                int childPrio  = ((MessageContentView) child).getPriority();
                // we have a chance to be added at this index
                if (childPrio >= prio) {
                    position = i;
                    break;
                    // TODO items with same priority should be ordered as inserted
                }

            }
        }

        return position;
    }

    public void addContent(MessageContentView<?> view) {
        addView((View) view, getPositionFromPriority(view));
    }

}
