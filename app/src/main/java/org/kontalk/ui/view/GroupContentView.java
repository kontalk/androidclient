/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import com.vanniktech.emoji.EmojiTextView;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.GroupCommandComponent;


/**
 * Message component for {@link GroupCommandComponent}.
 * @author Daniele Ricci
 */
public class GroupContentView extends EmojiTextView
    implements MessageContentView<GroupCommandComponent> {

    private GroupCommandComponent mComponent;

    public GroupContentView(Context context) {
        super(context);
    }

    public GroupContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void bind(long id, GroupCommandComponent component, Pattern highlight) {
        mComponent = component;

        CharSequence text = null;

        // create group
        if (component.isCreateCommand()) {
            text = new StringBuilder();
            ((StringBuilder) text).append(getResources().getString(R.string.group_command_create));
            for (String member : component.getCreateMembers()) {
                // TODO use something more "colorful"
                ((StringBuilder) text).append("\n");

                Contact c = Contact.findByUserId(getContext(), member);
                ((StringBuilder) text).append(c.getDisplayName());
            }
        }

        // member left group
        else if (component.isPartCommand()) {
            // sending to the group JID, this is our own part command
            if (component.getContent().getJid().equals(component.getFrom())) {
                text = getResources().getString(R.string.group_command_self_parted);
            }
            else {
                Contact c = Contact.findByUserId(getContext(), component.getFrom());
                boolean isOwner = mComponent.getContent().getOwner().equals(component.getFrom());
                text = getResources().getString(isOwner ?
                    R.string.group_command_owner_parted : R.string.group_command_user_parted,
                    c.getDisplayName());
            }
        }

        // add/remove members and set subject
        else if (component.isSetSubjectCommand()) {
            text = new StringBuilder();

            String subject = component.getContent().getSubject();
            if (subject != null) {
                if (text.length() > 0)
                    ((StringBuilder) text).append("\n");
                ((StringBuilder) text).append(getResources().getString(R.string.group_command_subject, subject));
            }
        }

        else if (component.isAddOrRemoveCommand()) {
            text = new StringBuilder();

            // add member(s)
            String[] added = component.getAddedMembers();
            if (added != null && added.length > 0) {
                ((StringBuilder) text).append(getResources().getString(R.string.group_command_users_added));
                for (String member : added) {
                    // TODO use something more "colorful"
                    ((StringBuilder) text).append("\n");

                    Contact c = Contact.findByUserId(getContext(), member);
                    ((StringBuilder) text).append(c.getDisplayName());
                }
            }

            // remove member(s)
            String[] removed = component.getRemovedMembers();
            if (removed != null && removed.length > 0) {
                if (text.length() > 0)
                    ((StringBuilder) text).append("\n");
                ((StringBuilder) text).append(getResources().getString(R.string.group_command_users_removed));
                for (String member : removed) {
                    // TODO use something more "colorful"
                    ((StringBuilder) text).append("\n");

                    Contact c = Contact.findByUserId(getContext(), member);
                    ((StringBuilder) text).append(c.getDisplayName());
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

    @Override
    public void onApplyTheme(MessageListItemTheme theme) {
    }

    private void clear() {
        mComponent = null;
    }

    public static GroupContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (GroupContentView) inflater
            .inflate(R.layout.message_content_group, parent, false);
    }

}
