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

package org.kontalk.message;


/**
 * A generic message component.
 * @author Daniele Ricci
 */
public abstract class MessageComponent<T> {

    protected T mContent;
    protected long mLength;
    protected boolean mEncrypted;
    protected int mSecurityFlags;

    public MessageComponent(T content, long length, boolean encrypted, int securityFlags) {
        mContent = content;
        mLength = length;
        mEncrypted = encrypted;
        mSecurityFlags = securityFlags;
    }

    public T getContent() {
        return mContent;
    }

    public void setContent(T content) {
        this.mContent = content;
    }

    public long getLength() {
        return mLength;
    }

    public boolean isEncrypted() {
        return mEncrypted;
    }

    public int getSecurityFlags() {
        return mSecurityFlags;
    }

}
