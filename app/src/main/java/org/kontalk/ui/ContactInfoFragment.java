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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import pub.devrel.easypermissions.EasyPermissions;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.provider.Keyring;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.event.ConnectedEvent;
import org.kontalk.service.msgcenter.event.LastActivityEvent;
import org.kontalk.service.msgcenter.event.LastActivityRequest;
import org.kontalk.service.msgcenter.event.NoPresenceEvent;
import org.kontalk.service.msgcenter.event.PresenceRequest;
import org.kontalk.service.msgcenter.event.RosterLoadedEvent;
import org.kontalk.service.msgcenter.event.UserOfflineEvent;
import org.kontalk.service.msgcenter.event.UserOnlineEvent;
import org.kontalk.ui.view.ContactInfoBanner;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Permissions;
import org.kontalk.util.SystemUtils;
import org.kontalk.util.XMPPUtils;


/**
 * Contact information fragment
 * @author Daniele Ricci
 */
public class ContactInfoFragment extends Fragment
        implements Contact.ContactChangeListener, EasyPermissions.PermissionCallbacks {

    Contact mContact;

    private ContactInfoBanner mInfoBanner;
    private TextView mPhoneNumber;
    private ImageButton mCallButton;
    private TextView mFingerprint;
    private ImageButton mTrustStatus;
    View mTrustButtons;
    private TextView mUserId;

    /**
     * Available resources.
     */
    Set<String> mAvailableResources = new HashSet<>();

    String mLastActivityRequestId;

    private EventBus mServiceBus = MessageCenterService.bus();

    public static ContactInfoFragment newInstance(String userId) {
        ContactInfoFragment f = new ContactInfoFragment();
        Bundle data = new Bundle();
        data.putString("user", userId);
        f.setArguments(data);
        return f;
    }

    private void loadContact(String userId) {
        Context context = getContext();
        if (context == null)
            return;

        mContact = Contact.findByUserId(context, userId);

        mInfoBanner.bind(context, mContact);

        String number = mContact.getNumber();
        mPhoneNumber.setText(number != null ?
            MessageUtils.reformatPhoneNumber(number) : context.getString(R.string.peer_unknown));
        mCallButton.setVisibility(number != null ? View.VISIBLE : View.GONE);

        mUserId.setText(mContact.getJID());

        String fingerprint = mContact.getFingerprint();
        if (fingerprint != null) {
            mFingerprint.setText(PGP.formatFingerprint(fingerprint)
                .replaceFirst(" {2}", "\n"));
            mFingerprint.setTypeface(Typeface.MONOSPACE);

            int resId, textId, trustButtonsVisibility;

            if (mContact.isSelf(context)) {
                resId = R.drawable.ic_trust_verified;
                textId = R.string.trust_verified;
                trustButtonsVisibility = View.GONE;
                mTrustStatus.setEnabled(false);
            }
            else if (mContact.isKeyChanged()) {
                // the key has changed and was not trusted yet
                resId = R.drawable.ic_trust_unknown;
                textId = R.string.trust_unknown;
                trustButtonsVisibility = View.VISIBLE;
                mTrustStatus.setEnabled(true);
            }
            else {
                mTrustStatus.setEnabled(true);

                switch (mContact.getTrustedLevel()) {
                    case Keyring.TRUST_UNKNOWN:
                        resId = R.drawable.ic_trust_unknown;
                        textId = R.string.trust_unknown;
                        trustButtonsVisibility = View.VISIBLE;
                        break;
                    case Keyring.TRUST_IGNORED:
                        resId = R.drawable.ic_trust_ignored;
                        textId = R.string.trust_ignored;
                        trustButtonsVisibility = View.VISIBLE;
                        break;
                    case Keyring.TRUST_VERIFIED:
                        resId = R.drawable.ic_trust_verified;
                        textId = R.string.trust_verified;
                        trustButtonsVisibility = View.GONE;
                        break;
                    default:
                        resId = -1;
                        textId = -1;
                        trustButtonsVisibility = View.GONE;
                }
            }

            mTrustButtons.setVisibility(trustButtonsVisibility);

            if (resId > 0) {
                mTrustStatus.setImageResource(resId);
                mTrustStatus.setVisibility(View.VISIBLE);
                mTrustStatus.setContentDescription(getString(textId));
            }
            else {
                mTrustStatus.setImageDrawable(null);
                mTrustStatus.setVisibility(View.GONE);
            }
        }
        else {
            mFingerprint.setText(context.getString(R.string.peer_unknown));
            mFingerprint.setTypeface(Typeface.DEFAULT);
            mTrustStatus.setImageDrawable(null);
            mTrustStatus.setVisibility(View.GONE);
        }

        if (!mServiceBus.isRegistered(this)) {
            mServiceBus.register(this);
        }
        /*
        if (mReceiver == null) {
            // listen to roster entry status requests
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                    boolean isSubscribed = intent
                        .getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_FROM, false) &&
                        intent.getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_TO, false);
                    // TODO update something
                    // TODO mInfoBanner.bind(context, mContact);
                }
            };

            IntentFilter filter = new IntentFilter(MessageCenterService.ACTION_ROSTER_STATUS);
            mLocalBroadcastManager.registerReceiver(mReceiver, filter);
        }
        */
    }

    // TODO version event
    // TODO blocked event
    // TODO unblocked event
    // TODO subscribed event
    // TODO roster status event

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN_ORDERED)
    public void onConnected(ConnectedEvent event) {
        // reset available resources list
        mAvailableResources.clear();
        // reset any pending request
        mLastActivityRequestId = null;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN_ORDERED)
    public void onRosterLoaded(RosterLoadedEvent event) {
        requestPresence();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onUserOnline(UserOnlineEvent event) {
        final Context context = getContext();
        if (context == null)
            return;

        if (!mContact.getJID().equals(event.jid.asBareJid().toString())) {
            // not for us
            return;
        }

        mAvailableResources.add(event.jid.toString());

        CharSequence statusText;

        boolean isAway = (event.mode == Presence.Mode.away);
        if (isAway) {
            statusText = context.getString(R.string.seen_away_label);
        }
        else {
            statusText = context.getString(R.string.seen_online_label);
        }

        mInfoBanner.setSummary(statusText);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onUserOffline(UserOfflineEvent event) {
        final Context context = getContext();
        if (context == null)
            return;

        if (!mContact.getJID().equals(event.jid.asBareJid().toString())) {
            // not for us
            return;
        }

        CharSequence statusText = null;

        boolean removed = mAvailableResources.remove(event.jid.toString());

        /*
         * All available resources have gone. Mark
         * the user as offline immediately and use the
         * timestamp provided with the stanza (if any).
         */
        if (mAvailableResources.size() == 0) {
            if (removed) {
                // resource was removed now, mark as just offline
                statusText = formatLastSeenText(context,
                    context.getText(R.string.seen_moment_ago_label));
            }
            else {
                // resource is offline, request last activity
                if (mContact.getLastSeen() > 0) {
                    setLastSeenTimestamp(context, mContact.getLastSeen());
                }
                else if (mLastActivityRequestId == null) {
                    mLastActivityRequestId = StringUtils.randomString(6);
                    mServiceBus.post(new LastActivityRequest(mLastActivityRequestId, event.jid.asBareJid()));
                }
            }
        }

        if (statusText != null) {
            mInfoBanner.setSummary(statusText);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onNoUserPresence(NoPresenceEvent event) {
        final Context context = getContext();
        if (context == null)
            return;

        if (!mContact.getJID().equals(event.jid.asBareJid().toString())) {
            // not for us
            return;
        }

        // no roster entry found, awaiting subscription or not subscribed
        mInfoBanner.setSummary(context.getString(R.string.invitation_sent_label));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onLastActivity(LastActivityEvent event) {
        final Context context = getContext();
        if (context == null)
            return;

        if (event.id != null && event.id.equals(mLastActivityRequestId)) {
            mLastActivityRequestId = null;
            // ignore last activity if we had an available presence in the meantime
            if (mAvailableResources.size() == 0) {
                if (event.error == null && event.idleTime >= 0) {
                    setLastSeenSeconds(context, event.idleTime);
                }
                else {
                    mInfoBanner.setSummary(context.getString(R.string.seen_offline_label));
                }
            }
        }
    }

    private void setLastSeenTimestamp(Context context, long stamp) {
        mInfoBanner.setSummary(formatLastSeenText(context,
            MessageUtils.formatRelativeTimeSpan(context, stamp)));
    }

    void setLastSeenSeconds(Context context, long seconds) {
        mInfoBanner.setSummary(formatLastSeenText(context,
            MessageUtils.formatLastSeen(context, mContact, seconds)));
    }

    private CharSequence formatLastSeenText(Context context, CharSequence text) {
        return context.getString(R.string.contactinfo_last_seen, text);
    }

    private void requestPresence() {
        final Context context = getContext();
        if (context == null)
            return;

        // do not request presence for domain JIDs
        if (!XMPPUtils.isDomainJID(mContact.getJID())) {
            mServiceBus.post(new PresenceRequest(mContact.getJID()));
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.contact_info, container, false);

        mInfoBanner = view.findViewById(R.id.contact_info);
        mPhoneNumber = view.findViewById(R.id.contact_phone);
        mCallButton = view.findViewById(R.id.btn_call);
        mCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callContact();
            }
        });

        mFingerprint = view.findViewById(R.id.fingerprint);
        mTrustStatus = view.findViewById(R.id.btn_trust_status);
        mUserId = view.findViewById(R.id.userid);

        mTrustButtons = view.findViewById(R.id.trust_button_bar);
        mTrustStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mContact.isSelf(view.getContext())) {
                    // TODO animate this
                    mTrustButtons.setVisibility(mTrustButtons.getVisibility() == View.VISIBLE ?
                        View.GONE : View.VISIBLE);
                }
            }
        });

        view.findViewById(R.id.btn_ignore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                trustKey(mContact.getFingerprint(), Keyring.TRUST_IGNORED);
            }
        });
        view.findViewById(R.id.btn_refuse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                trustKey(mContact.getFingerprint(), Keyring.TRUST_UNKNOWN);
            }
        });
        view.findViewById(R.id.btn_accept).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                trustKey(mContact.getFingerprint(), Keyring.TRUST_VERIFIED);
            }
        });

        return view;
    }

    void callContact() {
        final Context context = getContext();

        if (Permissions.canCallPhone(context)) {
            doCallContact();
        }
        else if (EasyPermissions.permissionPermanentlyDenied(this, Manifest.permission.CALL_PHONE)) {
            doDialContact();
        }
        else {
            Permissions.requestCallPhone(this);
        }
    }

    private void doCallContact() {
        SystemUtils.call(getContext(), mContact.getNumber());
    }

    private void doDialContact() {
        SystemUtils.dial(getContext(), mContact.getNumber());
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        if (perms.contains(Manifest.permission.CALL_PHONE)) {
            doCallContact();
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (perms.contains(Manifest.permission.CALL_PHONE)) {
            doDialContact();
        }
    }

    void trustKey(String fingerprint, int trustLevel) {
        String jid = mContact.getJID();
        Kontalk.get().getMessagesController()
            .setTrustLevelAndRetryMessages(jid, fingerprint, trustLevel);
        Contact.invalidate(jid);
        reload();
    }

    @Override
    public void onContactInvalidated(String userId) {
        Activity context = getActivity();
        if (context != null) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // just reload
                    reload();
                }
            });
        }
    }

    void reload() {
        // reload conversation data
        Bundle data = getArguments();
        String userId = data.getString("user");
        loadContact(userId);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onPause() {
        super.onPause();
        mServiceBus.unregister(this);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof ContactInfoParent))
            throw new IllegalArgumentException("parent activity must implement " +
                ContactInfoParent.class.getSimpleName());
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    public interface ContactInfoParent {

        void dismiss();

    }

}
