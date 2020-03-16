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

package org.kontalk.client;

import java.io.IOException;
import java.io.InputStream;

import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.provider.ProviderFileLoader;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.iqregister.provider.RegistrationProvider;
import org.jivesoftware.smackx.iqversion.VersionManager;
import org.jivesoftware.smackx.xdata.provider.DataFormProvider;
import org.minidns.dnsserverlookup.android21.AndroidUsingLinkProperties;

import android.content.Context;

import org.kontalk.R;


/**
 * Initializes the Smack subsystem.
 * @author Daniele Ricci
 */
public class SmackInitializer {

    private static boolean sInitialized;

    public static void initialize(Context context) {
        if (!sInitialized) {
            disableSmackDefault();

            // DNS for Android stuff
            // won't be used if not needed/available
            AndroidUsingLinkProperties.setup(context);

            InputStream is = context.getResources().openRawResource(R.raw.service);
            ProviderManager.addLoader(new ProviderFileLoader(is));
            try {
                is.close();
            }
            catch (IOException ignored) {
            }

            // FIXME these got to be fixed somehow (VCard4 is not even used anymore)
            ProviderManager.addIQProvider(VCard4.ELEMENT_NAME, VCard4.NAMESPACE, new VCard4.Provider());
            ProviderManager.addIQProvider(ServerlistCommand.ELEMENT_NAME, ServerlistCommand.NAMESPACE, new ServerlistCommand.ResultProvider());

            // do not append Smack version
            VersionManager.setAutoAppendSmackVersion(false);

            // we want to manually handle roster stuff
            Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.manual);

            sInitialized = true;
        }
    }

    /**
     * Initializes Smack for registration.
     */
    public static void initializeRegistration() {
        disableSmackDefault();
        // not moving these into configuration since they are not loaded often
        ProviderManager.addIQProvider("query", "jabber:iq:register", new RegistrationProvider());
        ProviderManager.addExtensionProvider("x", "jabber:x:data", new DataFormProvider());
        ProviderManager.addExtensionProvider(Account.ELEMENT_NAME, Account.NAMESPACE, new Account.Provider());
    }

    public static void deinitializeRegistration() {
        ProviderManager.removeIQProvider("query", "jabber:iq:register");
        ProviderManager.removeExtensionProvider("x", "jabber:x:data");
        ProviderManager.removeExtensionProvider(Account.ELEMENT_NAME, Account.NAMESPACE);
    }

    private static void disableSmackDefault() {
        // disable extensions and experimental - we will load our own extensions
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smack.extensions.ExtensionsInitializer");
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smack.experimental.ExperimentalInitializer");
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smack.ReconnectionManager");
    }

}
