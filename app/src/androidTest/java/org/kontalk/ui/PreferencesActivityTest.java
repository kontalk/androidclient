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

package org.kontalk.ui;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.action.GeneralLocation;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kontalk.R;
import org.kontalk.util.ClickWithoutDisplayConstraint;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.pressKey;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.kontalk.util.IsEqualTrimmingAndIgnoringCase.equalToTrimmingAndIgnoringCase;
import static org.kontalk.util.VisibleViewMatcher.isVisible;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class PreferencesActivityTest {

    @Rule
    public ActivityTestRule<ConversationsActivity> mActivityTestRule =
        new ActivityTestRule<>(ConversationsActivity.class);

    @Test
    public void preferencesActivityTest() {
        ViewInteraction android_widget_EditText =
            onView(
                Matchers.allOf(
                    ViewMatchers.withId(R.id.phone_number),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("5555215554")),
                    isVisible()));
        android_widget_EditText.perform(replaceText("974319006166743"));

        onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_ENTER));

        ViewInteraction android_widget_EditText2 =
            onView(
                allOf(
                    withId(R.id.name),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("Your name")),
                    isVisible()));
        android_widget_EditText2.perform(replaceText("crossbar's"));

        onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_ENTER));

        Espresso.pressBackUnconditionally();

        ViewInteraction android_widget_Button =
            onView(
                allOf(
                    withId(R.id.button_validate),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("REGISTER")),
                    isVisible()));
        android_widget_Button.perform(getClickAction());

        ViewInteraction android_widget_TextView =
            onView(
                allOf(
                    withId(R.id.md_buttonDefaultPositive),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("OK")),
                    isVisible(),
                    isDescendantOfA(withId(R.id.md_root))));
        android_widget_TextView.perform(getClickAction());

        Espresso.pressBackUnconditionally();

        onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_ENTER));

        ViewInteraction android_widget_Spinner = onView(allOf(withId(R.id.phone_cc), isVisible()));
        android_widget_Spinner.perform(getClickAction());

        Espresso.pressBackUnconditionally();

        ViewInteraction android_widget_EditText3 =
            onView(
                allOf(
                    withId(R.id.name),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("crossbar's")),
                    isVisible()));
        android_widget_EditText3.perform(replaceText("dourines"));

        ViewInteraction android_widget_Button2 =
            onView(
                allOf(
                    withId(R.id.button_validate),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("REGISTER")),
                    isVisible()));
        android_widget_Button2.perform(getClickAction());

        ViewInteraction android_widget_TextView2 =
            onView(
                allOf(
                    withId(R.id.md_buttonDefaultPositive),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("OK")),
                    isVisible(),
                    isDescendantOfA(withId(R.id.md_root))));
        android_widget_TextView2.perform(getClickAction());

        ViewInteraction android_widget_TextView3 =
            onView(
                allOf(
                    withId(R.id.md_content),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("Requesting registration…")),
                    isVisible()));
        android_widget_TextView3.perform(getClickAction());

        Espresso.pressBackUnconditionally();

        ViewInteraction android_widget_Spinner2 = onView(allOf(withId(R.id.phone_cc), isVisible()));
        android_widget_Spinner2.perform(getClickAction());

        Espresso.pressBackUnconditionally();

        ViewInteraction android_widget_ImageView =
            onView(
                allOf(
                    withContentDescription(equalToTrimmingAndIgnoringCase("More options")),
                    isVisible(),
                    isDescendantOfA(withId(R.id.toolbar))));
        android_widget_ImageView.perform(getLongClickAction());

        ViewInteraction android_widget_ImageView2 =
            onView(
                allOf(
                    withContentDescription(equalToTrimmingAndIgnoringCase("More options")),
                    isVisible(),
                    isDescendantOfA(withId(R.id.toolbar))));
        android_widget_ImageView2.perform(getClickAction());

        Espresso.pressBackUnconditionally();

        onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_ENTER));

        ViewInteraction android_widget_ImageView3 =
            onView(
                allOf(
                    withContentDescription(equalToTrimmingAndIgnoringCase("More options")),
                    isVisible(),
                    isDescendantOfA(withId(R.id.toolbar))));
        android_widget_ImageView3.perform(getLongClickAction());

        ViewInteraction android_widget_EditText4 =
            onView(
                allOf(
                    withId(R.id.name),
                    withTextOrHint(equalToTrimmingAndIgnoringCase("dourines")),
                    isVisible()));
        android_widget_EditText4.perform(replaceText("rewashing"));

        ViewInteraction android_widget_TextView4 =
            onView(
                allOf(withId(R.id.menu_settings), isVisible(), isDescendantOfA(withId(R.id.toolbar))));
        android_widget_TextView4.perform(getClickAction());

        Espresso.pressBackUnconditionally();

        Espresso.pressBackUnconditionally();
    }

    private static Matcher<View> withTextOrHint(final Matcher<String> stringMatcher) {
        return anyOf(withText(stringMatcher), withHint(stringMatcher));
    }

    private ClickWithoutDisplayConstraint getClickAction() {
        return new ClickWithoutDisplayConstraint(
            Tap.SINGLE,
            GeneralLocation.VISIBLE_CENTER,
            Press.FINGER,
            InputDevice.SOURCE_UNKNOWN,
            MotionEvent.BUTTON_PRIMARY);
    }

    private ClickWithoutDisplayConstraint getLongClickAction() {
        return new ClickWithoutDisplayConstraint(
            Tap.LONG,
            GeneralLocation.CENTER,
            Press.FINGER,
            InputDevice.SOURCE_UNKNOWN,
            MotionEvent.BUTTON_PRIMARY);
    }
}
