/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.kontalk.R;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.DefaultAttachmentComponent;
import org.kontalk.util.Preferences;


/**
 * Message component for {@link DefaultAttachmentComponent}.
 * @author Daniele Ricci
 */
public class DefaultAttachmentContentView extends AppCompatTextView
    implements MessageContentView<DefaultAttachmentComponent> {

    private DefaultAttachmentComponent mComponent;

    public DefaultAttachmentContentView(Context context) {
        super(context);
    }

    public DefaultAttachmentContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DefaultAttachmentContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void bind(long id, DefaultAttachmentComponent component, Pattern highlight) {
        mComponent = component;

        Context context = getContext();
        String size = Preferences.getFontSize(context);
        int sizeId;
        if (size.equals("small"))
            sizeId = android.R.style.TextAppearance_Small;
        else if (size.equals("large"))
            sizeId = android.R.style.TextAppearance_Large;
        else
            sizeId = android.R.style.TextAppearance;
        setTextAppearance(context, sizeId);

        String text = CompositeMessage.getSampleTextContent(component.getMime());
        setText(text);
    }

    @Override
    public void unbind() {
        clear();
    }

    @Override
    public DefaultAttachmentComponent getComponent() {
        return mComponent;
    }

    @Override
    public int getPriority() {
        return 6;
    }

    private void clear() {
        mComponent = null;
    }

    public static DefaultAttachmentContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (DefaultAttachmentContentView) inflater.inflate(R.layout.message_content_default_att,
            parent, false);
    }

}
