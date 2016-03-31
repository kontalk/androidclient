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
import org.kontalk.client.GroupExtension;
import org.kontalk.data.Contact;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.message.GroupComponent;
import org.kontalk.util.Preferences;


/**
 * Message component for {@link GroupComponent}.
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

        // TODO build some text based on the command
        StringBuilder text = new StringBuilder();
        GroupExtension ext = mComponent.getContent();

        // create group
        if (ext.getType() == GroupExtension.Type.CREATE) {
            // TODO i18n
            text.append("Group has been created.");
            for (GroupExtension.Member member : ext.getMembers()) {
                if (member.operation != GroupExtension.Member.Operation.NONE)
                    continue;

                text.append("\n-");

                Contact c = Contact.findByUserId(getContext(), member.jid);
                if (c != null)
                    text.append(c.getName());
                else
                    text.append(getResources().getString(R.string.peer_unknown));
            }
        }

        // member left group
        else if (ext.getType() == GroupExtension.Type.PART) {
            // TODO i18n
            Contact c = Contact.findByUserId(getContext(), component.getFrom());
            if (c != null)
                text.append(c);
            else
                text.append(getResources().getString(R.string.peer_unknown));

            text.append(" has left the group.");
        }

        // TODO add member(s)
        // TODO remove member(s)

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
