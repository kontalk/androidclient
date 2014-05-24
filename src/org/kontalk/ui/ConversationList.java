/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.authenticator.LegacyAuthentication;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.util.Preferences;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.Toast;


/**
 * The conversations list activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ConversationList extends ActionBarActivity
        implements ContactsSyncActivity, ContactPickerListener {
    //private static final String TAG = ConversationList.class.getSimpleName();

    private ConversationListFragment mFragment;

    private static final int REQUEST_CONTACT_PICKER = 7720;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_list_screen);

        mFragment = (ConversationListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_conversation_list);

        xmppUpgrade();
    }

    public void titleComposeMessage(View view) {
        getListFragment().chooseContact();
    }

    public void titleSearch(View view) {
        onSearchRequested();
    }

    /** Big upgrade: asymmetric key encryption (for XMPP). */
    private void xmppUpgrade() {
        AccountManager am = (AccountManager) getSystemService(Context.ACCOUNT_SERVICE);
        Account account = Authenticator.getDefaultAccount(am);
        if (account != null) {
        	// adjust manual server address if any
        	String manualServer = Preferences.getServerURI(this);
        	if (!TextUtils.isEmpty(manualServer) && manualServer.indexOf('|') < 0) {
        		ServerList list = ServerListUpdater.getCurrentList(this);
        		if (list != null) {
        			EndpointServer server = list.random();
        			String newServer = server.getNetwork() + "|" + manualServer;

        			Preferences.setServerURI(this, newServer);
        		}
        	}

            if (!Authenticator.hasPersonalKey(am, account))
            	askForPersonalName();
        }
    }

    private void askForPersonalName() {
    	DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
                // no key pair found, generate a new one
                Toast.makeText(ConversationList.this,
                	R.string.msg_generating_keypair, Toast.LENGTH_LONG).show();

                String name = InputDialog
                		.getTextFromAlertDialog((AlertDialog) dialog)
                		.toString();

                // upgrade account
                LegacyAuthentication.doUpgrade(getApplicationContext(), name);

			}
		};

		DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				new AlertDialog.Builder(ConversationList.this)
					.setTitle(R.string.title_no_personal_key)
					.setMessage(R.string.msg_no_personal_key)
					.setPositiveButton(android.R.string.ok, null)
					.show();
			}
		};

    	new InputDialog.Builder(this,
    			InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
    		.setTitle(R.string.title_no_name)
    		.setMessage(R.string.msg_no_name)
    		.setPositiveButton(android.R.string.ok, okListener)
    		.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			})
    		.setOnCancelListener(cancelListener)
    		.show();
    }

    /** Called when a new intent is sent to the activity (if already started). */
    @Override
    protected void onNewIntent(Intent intent) {
        ConversationListFragment fragment = getListFragment();
        fragment.startQuery();
    }

    @Override
    public boolean onSearchRequested() {
        ConversationListFragment fragment = getListFragment();

        ListAdapter list = fragment.getListAdapter();
        // no data found
        if (list == null || list.getCount() == 0)
            return false;

        startSearch(null, false, null, false);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Authenticator.getDefaultAccount(this) == null) {
            NumberValidation.startValidation(this);
            finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // contact chooser
        if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null)
                    openConversation(uri);
            }
        }
    }

    public ConversationListFragment getListFragment() {
        return mFragment;
    }

    public boolean isDualPane() {
        return findViewById(R.id.fragment_compose_message) != null;
    }

    /** Called when a contact has been selected from a {@link ContactsListFragment}. */
    @Override
    public void onContactSelected(ContactsListFragment fragment, Contact contact) {
        // open by user hash
        openConversation(Threads.getUri(contact.getHash()));
    }

    public void showContactPicker() {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(this, ContactsListActivity.class);
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    @Override
    public void setSyncing(boolean syncing) {
        // TODO
    }

    public void openConversation(Conversation conv, int position) {
        if (isDualPane()) {
            mFragment.getListView().setItemChecked(position, true);

            // get the old fragment
            ComposeMessageFragment f = (ComposeMessageFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_compose_message);

            // check if we are replacing the same fragment
            if (f == null || !f.getConversation().getRecipient().equals(conv.getRecipient())) {
                f = ComposeMessageFragment.fromConversation(this, conv);
                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_compose_message, f);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                ft.addToBackStack(null);
                ft.commit();
            }
        }
        else {
            Intent i = ComposeMessage.fromConversation(this, conv);
            startActivity(i);
        }
    }

    private void openConversation(Uri threadUri) {
        if (isDualPane()) {
            // TODO position
            //mFragment.getListView().setItemChecked(position, true);

            // load conversation
            String userId = threadUri.getLastPathSegment();
            Conversation conv = Conversation.loadFromUserId(this, userId);

            // get the old fragment
            ComposeMessageFragment f = (ComposeMessageFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_compose_message);

            // check if we are replacing the same fragment
            if (f == null || conv == null || !f.getConversation().getRecipient().equals(conv.getRecipient())) {
                if (conv == null)
                    f = ComposeMessageFragment.fromUserId(this, userId);
                else
                    f = ComposeMessageFragment.fromConversation(this, conv);

                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_compose_message, f);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.addToBackStack(null);
                ft.commitAllowingStateLoss();
            }
        }
        else {
            Intent i = ComposeMessage.fromUserId(this, threadUri.getLastPathSegment());
            if (i != null)
                startActivity(i);
            else
                Toast.makeText(this, R.string.contact_not_registered, Toast.LENGTH_LONG)
                    .show();
        }
    }

}
