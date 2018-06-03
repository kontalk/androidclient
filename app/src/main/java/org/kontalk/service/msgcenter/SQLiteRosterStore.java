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

package org.kontalk.service.msgcenter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.roster.rosterstore.RosterStore;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import org.kontalk.util.Preferences;


/**
 * A roster store backed by a SQLite database.
 * @author Daniele Ricci
 */
public class SQLiteRosterStore extends SQLiteOpenHelper implements RosterStore {

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "roster.db";

    private static final String TABLE_ROSTER = "roster";
    private static final String CREATE_TABLE_ROSTER = "(" +
        "jid TEXT NOT NULL PRIMARY KEY," +
        "name TEXT NOT NULL," +
        "type TEXT NOT NULL," +
        "pending BOOLEAN NOT NULL,"+
        "approved BOOLEAN NOT NULL,"+
        "groups TEXT"+
        ")";

    private static final String SCHEMA_ROSTER =
        "CREATE TABLE " + TABLE_ROSTER + " " + CREATE_TABLE_ROSTER;

    private static final String[] SCHEMA_UPGRADE = {
        "DROP TABLE roster",
        SCHEMA_ROSTER,
    };

    private final Context mContext;

    private SQLiteStatement mInsertStatement;
    private final Object mInsertLock = new Object();

    public SQLiteRosterStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SCHEMA_ROSTER);
        setRosterVersion("");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (String sql : SCHEMA_UPGRADE) {
            db.execSQL(sql);
        }
        setRosterVersion("");
    }

    public void onDestroy() {
        close();
    }

    private SQLiteStatement prepareInsert(SQLiteDatabase db, RosterPacket.Item item) {
        if (mInsertStatement == null) {
            mInsertStatement = db.compileStatement("INSERT OR REPLACE INTO " + TABLE_ROSTER +
                " VALUES(?, ?, ?, ?, ?, ?)");
        }
        else {
            mInsertStatement.clearBindings();
        }

        int i = 0;
        mInsertStatement.bindString(++i, item.getJid().toString());
        mInsertStatement.bindString(++i, item.getName());
        mInsertStatement.bindString(++i, item.getItemType() != null ?
            item.getItemType().toString() : RosterPacket.ItemType.none.toString());
        mInsertStatement.bindLong(++i, item.isSubscriptionPending() ? 1 : 0);
        mInsertStatement.bindLong(++i, item.isApproved() ? 1 : 0);


        Set<String> groups = item.getGroupNames();
        if (groups != null) {
            mInsertStatement.bindString(++i, TextUtils.join(",", groups));
        }
        else {
            mInsertStatement.bindNull(++i);
        }

        return mInsertStatement;
    }

    @Override
    public List<RosterPacket.Item> getEntries() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query(TABLE_ROSTER, null, null, null, null, null, null);
            if (c != null) {
                List<RosterPacket.Item> items = new ArrayList<>(c.getCount());
                while (c.moveToNext()) {
                    items.add(fromCursor(c));
                }

                return items;
            }

            return null;
        }
        catch (SQLiteException e) {
            return null;
        }
        catch (XmppStringprepException e) {
            return null;
        }
        finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private RosterPacket.Item fromCursor(Cursor c) throws XmppStringprepException {
        String user = c.getString(0);
        String name = c.getString(1);
        RosterPacket.Item item = new RosterPacket.Item(JidCreate.bareFrom(user), name);
        String type = c.getString(2);
        if (type == null)
            type = RosterPacket.ItemType.none.toString();
        item.setItemType(RosterPacket.ItemType.valueOf(type));

        boolean pending = c.getInt(3) != 0;
        item.setSubscriptionPending(pending);

        boolean approved = c.getInt(4) != 0;
        item.setApproved(approved);

        String groups = c.getString(5);
        if (groups != null) {
            StringTokenizer tokenizer = new StringTokenizer(groups, ",");
            while (tokenizer.hasMoreTokens()) {
                item.addGroupName(tokenizer.nextToken());
            }
        }

        return item;
    }

    @Override
    public RosterPacket.Item getEntry(Jid bareJid) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query(TABLE_ROSTER, null,
                "jid = ?", new String[] { bareJid.toString() },
                null, null, null);
            if (c != null && c.moveToFirst()) {
                return fromCursor(c);
            }
        }
        catch (SQLiteException e) {
            return null;
        }
        catch (XmppStringprepException e) {
            return null;
        }
        finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    @Override
    public String getRosterVersion() {
        return Preferences.getRosterVersion();
    }

    private boolean addEntry(SQLiteDatabase db, RosterPacket.Item item) {
        synchronized (mInsertLock) {
            try {
                SQLiteStatement stm = prepareInsert(db, item);
                stm.executeInsert();
            }
            catch (SQLiteException e) {
                return false;
            }

            // insert was successful
            return true;
        }
    }

    @Override
    public boolean addEntry(RosterPacket.Item item, String version) {
        SQLiteDatabase db = getWritableDatabase();
        return addEntry(db, item) && setRosterVersion(version);
    }

    @Override
    public void resetStore() {
        SQLiteDatabase db = getWritableDatabase();

        beginTransaction(db);
        boolean success = false;

        try {
            db.execSQL("DELETE FROM " + TABLE_ROSTER);

            success = setTransactionSuccessful(db);
            if (success) {
                setRosterVersion("");
            }
        }
        catch (SQLiteException ignored) {
        }
        finally {
            endTransaction(db, success);
        }
    }

    @Override
    public boolean resetEntries(Collection<RosterPacket.Item> items, String version) {
        SQLiteDatabase db = getWritableDatabase();

        beginTransaction(db);
        boolean success = false;

        try {
            db.execSQL("DELETE FROM " + TABLE_ROSTER);
            for (RosterPacket.Item item : items) {
                addEntry(db, item);
            }

            success = setTransactionSuccessful(db);
            if (success) {
                setRosterVersion(version);
            }
        }
        catch (SQLiteException e) {
            return false;
        }
        finally {
            endTransaction(db, success);
        }

        return false;
    }

    @Override
    public boolean removeEntry(Jid bareJid, String version) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_ROSTER, "jid = ?", new String[]{bareJid.toString()});
            return setRosterVersion(version);
        }
        catch (SQLiteException e) {
            return false;
        }
    }

    private boolean setRosterVersion(String version) {
        return Preferences.setRosterVersion(version);
    }

    public static void purge(Context context) {
        new SQLiteRosterStore(context).resetStore();
    }

    /* Transactions compatibility layer */

    @TargetApi(android.os.Build.VERSION_CODES.HONEYCOMB)
    private void beginTransaction(SQLiteDatabase db) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
            db.beginTransactionNonExclusive();
        else
            // this is because API < 11 doesn't have beginTransactionNonExclusive()
            db.execSQL("BEGIN IMMEDIATE");
    }

    private boolean setTransactionSuccessful(SQLiteDatabase db) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
            db.setTransactionSuccessful();
        return true;
    }

    private void endTransaction(SQLiteDatabase db, boolean success) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
            db.endTransaction();
        else
            db.execSQL(success ? "COMMIT" : "ROLLBACK");
    }

}
