/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.regex.Pattern;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.util.Preferences;


/**
 * Message component for {@link GroupCommandComponent}.
 * @author Daniele Ricci
 */
public class GroupContentView extends TextView
    implements MessageContentView<GroupCommandComponent> {

    private GroupCommandComponent mComponent;

    public GroupContentView(Context context) {
        super(context);
    }

    public GroupContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GroupContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void bind(long id, GroupCommandComponent component, Pattern highlight) {
        mComponent = component;

        Context context = getContext();
        String size = Preferences.getFontSize(context);
        int sizeId;
        if (size.equals("small"))
            sizeId = R.style.TextAppearance_CommandMessage_Small;
        else if (size.equals("large"))
            sizeId = R.style.TextAppearance_CommandMessage_Large;
        else
            sizeId = R.style.TextAppearance_CommandMessage;
        setTextAppearance(context, sizeId);

        CharSequence text = null;

        // create group
        if (component.isCreateCommand()) {
            text = new StringBuilder();
            ((StringBuilder) text).append(getResources().getString(R.string.group_command_create));
            for (String member : component.getExistingMembers()) {
                // TODO use something more "colorful"
                ((StringBuilder) text).append("\n");

                Contact c = Contact.findByUserId(getContext(), member);
                if (c != null)
                    ((StringBuilder) text).append(c.getName());
                else
                    ((StringBuilder) text).append(member);
            }
        }

        // member left group
        else if (component.isPartCommand()) {
            Contact c = Contact.findByUserId(getContext(), component.getFrom());
            text = getResources().getString(R.string.group_command_user_parted,
                (c != null) ? c.getName() : getResources().getString(R.string.peer_unknown));
        }

        // add/remove members and set subject
        else if (component.isSetSubjectCommand()) {
            text = new StringBuilder();

            if (component.isAddOrRemoveCommand()) {
                // add member(s)
                String[] added = component.getAddedMembers();
                if (added != null && added.length > 0) {
                    // TODO i18n
                    ((StringBuilder) text).append("Added users:");
                    for (String member : added) {
                        // TODO use something more "colorful"
                        ((StringBuilder) text).append("\n");

                        Contact c = Contact.findByUserId(getContext(), member);
                        if (c != null)
                            ((StringBuilder) text).append(c.getName());
                        else
                            ((StringBuilder) text).append(member);
                    }
                }

                // remove member(s)
                String[] removed = component.getRemovedMembers();
                if (removed != null && removed.length > 0) {
                    if (text.length() > 0)
                        ((StringBuilder) text).append("\n");
                    // TODO i18n
                    ((StringBuilder) text).append("Removed users:");
                    for (String member : removed) {
                        // TODO use something more "colorful"
                        ((StringBuilder) text).append("\n");

                        Contact c = Contact.findByUserId(getContext(), member);
                        if (c != null)
                            ((StringBuilder) text).append(c.getName());
                        else
                            ((StringBuilder) text).append(member);
                    }
                }
            }

            else {
                String subject = component.getContent().getSubject();
                if (subject != null) {
                    if (text.length() > 0)
                        ((StringBuilder) text).append("\n");
                    ((StringBuilder) text).append(getResources().getString(R.string.group_command_subject, subject));
                }
            }
        }

        setText(text);
    }

    @Override
    public void unbind() {
        clear();
    }

    @Override
    public GroupCommandComponent getComponent() {
        return mComponent;
    }

    @Override
    public int getPriority() {
        return 7;
    }

    private void clear() {
        mComponent = null;
    }

    public static GroupContentView create(LayoutInflater inflater, ViewGroup parent) {
        GroupContentView view = (GroupContentView) inflater
            .inflate(R.layout.message_content_group, parent, false);
        return view;
    }

}
