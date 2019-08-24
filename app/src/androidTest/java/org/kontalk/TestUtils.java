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

package org.kontalk;

import java.util.concurrent.TimeUnit;

import org.greenrobot.eventbus.EventBus;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;

import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.database.Cursor;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.IdlingPolicies;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.TextComponent;
import org.kontalk.util.MessageUtils;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;


public class TestUtils {
    static {
        BuildConfig.TESTING.set(true);
    }

    private TestUtils() {
    }

    public static void skipIfDefaultAccountDoesNotExist() {
        assumeFalse("default account does not exist - skipping test",
            Authenticator.getDefaultAccount
                (InstrumentationRegistry.getInstrumentation().getTargetContext()) == null);
    }

    public static void skipIfDefaultAccountExists() {
        assumeTrue("default account exists - skipping test",
            Authenticator.getDefaultAccount
                (InstrumentationRegistry.getInstrumentation().getTargetContext()) == null);
    }

    public static void assertDefaultAccountExists() {
        assertThat("default account does not exist",
            Authenticator.getDefaultAccount
                (InstrumentationRegistry.getInstrumentation().getTargetContext()),
            notNullValue());
    }

    public static void removeDefaultAccount() throws InterruptedException {
        final Object monitor = new Object();
        AccountManagerCallback<Boolean> callback = new AccountManagerCallback<Boolean>() {
            public void run(AccountManagerFuture<Boolean> future) {
                synchronized (monitor) {
                    monitor.notify();
                }
            }
        };
        Authenticator.removeDefaultAccount(InstrumentationRegistry.getInstrumentation().getTargetContext(), callback);
        synchronized (monitor) {
            monitor.wait(3000);
        }
    }

    public static Matcher<View> withMessageDirection(final int direction) {
        return new CustomMatcher<View>("with message direction: ") {
            @Override
            public boolean matches(Object item) {
                if (!(item instanceof AdapterView)) {
                    return false;
                }

                Adapter adapter = ((AdapterView) item).getAdapter();
                for (int i = 0; i < adapter.getCount(); i++) {
                    Cursor message = (Cursor) adapter.getItem(i);
                    if (message != null && MessageUtils.getMessageDirection(message) == direction) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public static Matcher<View> withMessageContent(final Context context, final String content) {
        return new CustomMatcher<View>("with message content: ") {
            @Override
            public boolean matches(Object item) {
                if (!(item instanceof AdapterView)) {
                    return false;
                }

                Adapter adapter = ((AdapterView) item).getAdapter();
                for (int i = 0; i < adapter.getCount(); i++) {
                    Cursor cursor = (Cursor) adapter.getItem(i);
                    if (cursor != null) {
                        CompositeMessage message = CompositeMessage.fromCursor(context, cursor);
                        TextComponent text = message.getComponent(TextComponent.class);
                        if (text != null && text.getContent().equalsIgnoreCase(content)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    public static EventIdlingResource registerEventIdlingResource(EventBus bus, Class klass) {
        IdlingPolicies.setIdlingResourceTimeout(5, TimeUnit.MINUTES);
        IdlingPolicies.setMasterPolicyTimeout(5, TimeUnit.MINUTES);

        EventIdlingResource resource = new EventIdlingResource(klass.getSimpleName(), bus, klass);
        IdlingRegistry.getInstance().register(resource);
        return resource;
    }

    public static <T extends IdlingResource> T registerIdlingResource(T resource) {
        IdlingPolicies.setIdlingResourceTimeout(5, TimeUnit.MINUTES);
        IdlingPolicies.setMasterPolicyTimeout(5, TimeUnit.MINUTES);

        IdlingRegistry.getInstance().register(resource);
        return resource;
    }

    public static void unregisterIdlingResource(IdlingResource resource) {
        IdlingPolicies.setIdlingResourceTimeout(1, TimeUnit.MINUTES);
        IdlingPolicies.setMasterPolicyTimeout(1, TimeUnit.MINUTES);

        IdlingRegistry.getInstance().unregister(resource);
    }
}
