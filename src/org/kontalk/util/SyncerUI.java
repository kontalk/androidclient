package org.kontalk.util;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.provider.UsersProvider;
import org.kontalk.sync.Syncer;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentProviderClient;
import android.content.DialogInterface;
import android.content.SyncResult;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;


public abstract class SyncerUI {
    private static final String TAG = SyncerUI.class.getSimpleName();

    public static void execute(Activity context, Runnable finish, boolean dialog) {
        if (Syncer.getInstance() == null) {
            // start monitored sync
            Log.v(TAG, "starting monitored sync");
            new SyncerUI.SyncTask(context, finish, dialog).execute(Syncer.getInstance(context));
        }
        else {
            // monitor existing instance
            Log.v(TAG, "sync already in progress, monitoring it");
            new SyncerUI.SyncMonitorTask(context, finish, dialog).execute();
        }
    }

    /** Executes a sync asynchronously, monitoring its status. */
    public static final class SyncTask extends AsyncTask<Syncer, Integer, Boolean> {
        private final Activity context;
        private Syncer syncer;
        private Dialog dialog;
        private final Runnable finish;
        private final boolean useDialog;

        public SyncTask(Activity context, Runnable finish, boolean useDialog) {
            this.context = context;
            this.finish = finish;
            this.useDialog = useDialog;
        }

        @Override
        protected Boolean doInBackground(Syncer... params) {
            if (isCancelled())
                return false;

            String authority = ContactsContract.AUTHORITY;
            Account account = Authenticator.getDefaultAccount(context);
            ContentProviderClient provider = context.getContentResolver()
                    .acquireContentProviderClient(authority);
            ContentProviderClient usersProvider = context.getContentResolver()
                    .acquireContentProviderClient(UsersProvider.AUTHORITY);

            try {
                syncer = params[0];
                syncer.performSync(context, account,
                    authority, provider, usersProvider, new SyncResult());
            }
            catch (OperationCanceledException e) {
                // ignored - normal cancelation
            }
            catch (Exception e) {
                // TODO error string where??
                Log.e(TAG, "syncer error", e);
                return false;
            }
            finally {
                usersProvider.release();
                provider.release();
                Syncer.release();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Runnable action = null;
            if (!result) {
                action = new Runnable() {
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog
                                .Builder(context);
                        builder
                            // TODO i18n
                            .setTitle("Error")
                            .setMessage("Unable to refresh contacts list. Please retry later.")
                            .setPositiveButton(android.R.string.ok, null)
                            .create().show();
                    }
                };
            }

            finish(action);
        }

        @Override
        protected void onPreExecute() {
            if (useDialog) {
                ProgressDialog dg = new ProgressDialog(context);
                dg.setMessage(context.getString(R.string.msg_sync_progress));
                dg.setIndeterminate(true);
                dg.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        cancel(true);
                    }
                });

                dialog = dg;
                dg.show();
            }
        }

        @Override
        protected void onCancelled(Boolean result) {
            if (syncer != null)
                syncer.onSyncCanceled();
            finish(null);
        }

        private void finish(Runnable action) {
            // dismiss status dialog
            if (dialog != null)
                dialog.dismiss();
            if (action != null)
                action.run();
            if (finish != null)
                finish.run();
        }
    }

    /** Monitors an already running {@link Syncer}. */
    public static final class SyncMonitorTask extends AsyncTask<Syncer, Integer, Boolean> {
        private final Activity context;
        private Dialog dialog;
        private final Runnable finish;
        private final boolean useDialog;

        public SyncMonitorTask(Activity context, Runnable finish, boolean useDialog) {
            this.context = context;
            this.finish = finish;
            this.useDialog = useDialog;
        }

        @Override
        protected Boolean doInBackground(Syncer... params) {
            if (isCancelled())
                return false;

            while (Syncer.getInstance() != null) {
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)  {
                    // interrupted :)
                }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            finish();
        }

        @Override
        protected void onPreExecute() {
            if (useDialog) {
                ProgressDialog dg = new ProgressDialog(context);
                dg.setMessage(context.getString(R.string.msg_sync_progress));
                dg.setIndeterminate(true);
                dg.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        cancel(true);
                    }
                });

                dialog = dg;
                dg.show();
            }
        }

        @Override
        protected void onCancelled(Boolean result) {
            finish();
        }

        private void finish() {
            // dismiss status dialog
            if (dialog != null)
                dialog.dismiss();
            if (finish != null)
                finish.run();
        }
    }


}
