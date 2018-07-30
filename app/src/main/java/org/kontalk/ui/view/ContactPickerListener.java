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

import java.util.List;

import org.kontalk.data.Contact;
import org.kontalk.ui.ContactsListFragment;


/**
 * Implementors are supposed to be parents of the {@link org.kontalk.ui.ContactsListFragment}.
 * @author Daniele Ricci
 */
public interface ContactPickerListener {

    /** Called when a contact has been selected from a {@link org.kontalk.ui.ContactsListFragment}. */
    void onContactSelected(ContactsListFragment fragment, Contact contact);

    /**
     * Called when one or more contacts have been selected from a
     * {@link org.kontalk.ui.ContactsListFragment}.
     */
    void onContactsSelected(ContactsListFragment fragment, List<Contact> contacts);

}
