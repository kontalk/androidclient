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

package org.kontalk.ui;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.kontalk.DefaultAccountTest;
import org.kontalk.R;
import org.kontalk.TestUtils;
import org.kontalk.provider.MyMessages;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class QuickSelfChatTest extends DefaultAccountTest {
    private static final String TEST_MESSAGE = "Ping";

    @Rule
    public ActivityTestRule<ConversationsActivity> mActivityTestRule =
        new ActivityTestRule<>(ConversationsActivity.class);

    @Test
    public void quickSelfChatTest() {
        ViewInteraction floatingActionButton = onView(
            allOf(withClassName(is("com.github.clans.fab.FloatingActionButton")),
                withParent(allOf(withId(R.id.action),
                    withParent(withId(R.id.fragment_conversation_list)))),
                isDisplayed()));
        floatingActionButton.perform(click());

        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(
            allOf(withId(R.id.action_compose),
                withParent(allOf(withId(R.id.action),
                    withParent(withId(R.id.fragment_conversation_list)))),
                isDisplayed()))
            .perform(click());

        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(
            allOf(childAtPosition(
                withId(android.R.id.list),
                0),
                isDisplayed()))
            .perform(click());

        onView(
            allOf(withId(R.id.text_editor),
                withParent(allOf(withId(R.id.composer_bar),
                    withParent(withId(R.id.container)))),
                isDisplayed()))
            .perform(replaceText(TEST_MESSAGE), closeSoftKeyboard());

        onView(
            allOf(withId(R.id.send_button),
                isEnabled()))
            .perform(click());

        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(withId(android.R.id.list))
            .check(matches(TestUtils.withMessageDirection
                (MyMessages.Messages.DIRECTION_IN)))
            .check(matches(TestUtils.withMessageContent
                (InstrumentationRegistry.getInstrumentation().getTargetContext(), TEST_MESSAGE)));
    }

    private static Matcher<View> childAtPosition(
        final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                    && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}
