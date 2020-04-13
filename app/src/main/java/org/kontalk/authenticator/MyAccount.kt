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

package org.kontalk.authenticator

import android.accounts.Account
import android.accounts.AccountManager
import org.jxmpp.jid.BareJid
import org.jxmpp.util.XmppStringUtils
import org.kontalk.client.EndpointServer
import org.kontalk.client.ServerList
import org.kontalk.crypto.PersonalKey
import org.kontalk.util.XMPPUtils


/** A Kontalk account. */
class MyAccount (val systemAccount: Account, private val accountManager: AccountManager) {

    val displayName: String by lazy {
        Authenticator.getDisplayName(accountManager, systemAccount)
    }

    val server: EndpointServer by lazy {
        Authenticator.getServer(accountManager, systemAccount)
    }

    val serverList: ServerList by lazy {
        Authenticator.getServerList(accountManager, systemAccount)
    }

    val serviceTermsURL: String? by lazy {
        Authenticator.getServiceTermsURL(accountManager, systemAccount)
    }

    val phoneNumber: String = this.systemAccount.name

    val personalKey: PersonalKey by lazy {
        Authenticator.loadPersonalKey(accountManager, systemAccount)
    }

    val passphrase: String? by lazy {
        accountManager.getPassword(systemAccount)
    }

    val selfJID: String by lazy {
        createLocalJID(XMPPUtils.createLocalpart(getName()))
    }

    fun isSelfJID(bareJid: String): Boolean = selfJID.equals(bareJid, ignoreCase = true)

    fun isSelfJID(bareJid: BareJid): Boolean = bareJid.equals(selfJID)

    fun isNetworkJID(bareJid: BareJid): Boolean {
        return serverList.first { it.network.equals(bareJid.domain.toString(), true) } != null
    }

    fun createLocalJID(localpart: String): String {
        return XmppStringUtils.completeJidFrom(localpart, server.network);
    }

    /* compatibility interface for android.accounts.Account */

    fun getName(): String {
        return this.systemAccount.name
    }

    fun getType(): String {
        return this.systemAccount.type
    }

    override fun equals(other: Any?): Boolean {
        return this.systemAccount == other
    }

    override fun hashCode(): Int {
        return this.systemAccount.hashCode()
    }

    override fun toString(): String {
        return this.systemAccount.toString()
    }

}
