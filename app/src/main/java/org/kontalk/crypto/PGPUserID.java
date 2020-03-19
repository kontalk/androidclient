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

package org.kontalk.crypto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A PGP user id.
 * @author Daniele Ricci
 */
public class PGPUserID {
    private static final Pattern PATTERN_UID_FULL = Pattern.compile("^(.*) \\((.*)\\) <(.*)>$");
    private static final Pattern PATTERN_UID_NO_COMMENT = Pattern.compile("^(.*) <(.*)>$");
    private static final Pattern PATTERN_UID_EMAIL_ONLY = Pattern.compile("^(.*@.*)$");

    private final String name;
    private final String comment;
    private final String email;

    public PGPUserID(String name) {
        this(name, null, null);
    }

    public PGPUserID(String name, String email) {
        this(name, email, null);
    }

    public PGPUserID(String name, String comment, String email) {
        this.name = name;
        this.comment = comment;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();

        if (name == null && comment == null && email != null) {
            out.append(email);
        }
        else if (name != null) {
            out.append(name);
            if (comment != null)
                out.append(" (").append(comment).append(')');

            if (email != null)
                out.append(" <").append(email).append('>');
        }

        return out.toString();
    }

    public static PGPUserID parse(String uid) {
        Matcher match;

        match = PATTERN_UID_FULL.matcher(uid);
        while (match.find()) {
            if (match.groupCount() >= 3) {
                String name = match.group(1);
                String comment = match.group(2);
                String email = match.group(3);
                return new PGPUserID(name, comment, email);
            }
        }

        // try again without comment
        match = PATTERN_UID_NO_COMMENT.matcher(uid);
        while (match.find()) {
            if (match.groupCount() >= 2) {
                String name = match.group(1);
                String email = match.group(2);
                return new PGPUserID(name, null, email);
            }
        }

        // try again with email only
        match = PATTERN_UID_EMAIL_ONLY.matcher(uid);
        while (match.find()) {
            if (match.groupCount() >= 1) {
                String email = match.group(1);
                return new PGPUserID(null, null, email);
            }
        }

        // no match found
        return null;
    }

}
