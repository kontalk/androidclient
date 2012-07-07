package org.kontalk.util;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.provider.UsersProvider;
import org.kontalk.sync.Syncer;
import org.kontalk.ui.LockedProgressDialog;

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
    private static AsyncTask<Syncer, Integer, Boolean> currentSyncer;

    public static void execute(Activity context, Runnable finish, boolean dialog) {
        if (Authenticator.getDefaultAccount(context) == null) {
            Log.v(TAG, "account does not exists, skipping sync");
            return;
        }

        // another ongoing operation - replace finish runnable
        if (currentSyncer != null) {
            if (currentSyncer instanceof SyncTask) {
                ((SyncTask) currentSyncer).setFinish(finish);
            }
            else {
                ((SyncMonitorTask) currentSyncer).setFinish(finish);
            }
        }
        else {
            if (Syncer.getInstance() == null) {
                // start monitored sync
                Log.v(TAG, "starting monitored sync");
                currentSyncer = new SyncTask(context, finish, dialog).execute(Syncer.getInstance(context));
            }
            else {
                // monitor existing instance
                Log.v(TAG, "sync already in progress, monitoring it");
                currentSyncer = new SyncMonitorTask(context, finish, dialog).execute();
            }
        }
    }

    public synchronized static void cancel() {
        if (currentSyncer != null)
            currentSyncer.cancel(true);
    }

    /** Executes a sync asynchronously, monitoring its status. */
    public static final class SyncTask extends AsyncTask<Syncer, Integer, Boolean> {
        private final Activity context;
        private Syncer syncer;
        private Dialog dialog;
        private Runnable finish;
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
            SyncResult syncResult = new SyncResult();

            try {
                syncer = params[0];
                syncer.performSync(context, account,
                    authority, provider, usersProvider, syncResult);
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
            return !Syncer.isError(syncResult);
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
                            .setTitle(R.string.title_error)
                            .setMessage(R.string.err_sync_failed)
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

        /** This is because of a pre-Froyo bug. */
        @Override
        protected void onCancelled() {
            onCancelled(false);
        }

        @Override
        protected void onCancelled(Boolean result) {
            if (syncer != null)
                syncer.onSyncCanceled();
            finish(null);
        }

        private void finish(Runnable action) {
            // discard reference
            currentSyncer = null;

            // dismiss status dialog
            if (dialog != null)
                dialog.dismiss();

            // run in separate try/catch so we are sure that both gets executed

            try {
                action.run();
            }
            catch (Exception e) {
                // ignored
            }
            try {
                finish.run();
            }
            catch (Exception e) {
                // ignored
            }
        }

        public void setFinish(Runnable action) {
            finish = action;
            // TODO syncer.onSyncResumed();
        }
    }

    /** Monitors an already running {@link Syncer}. */
    public static final class SyncMonitorTask extends AsyncTask<Syncer, Integer, Boolean> {
        private final Activity context;
        private Dialog dialog;
        private Runnable finish;
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
                ProgressDialog dg = new LockedProgressDialog(context);
                dg.setMessage(context.getString(R.string.msg_sync_progress));
                dg.setCancelable(true);
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

        /** This is because of a pre-Froyo bug. */
        @Override
        protected void onCancelled() {
            onCancelled(false);
        }

        @Override
        protected void onCancelled(Boolean result) {
            finish();
        }

        private void finish() {
            // discard reference
            currentSyncer = null;

            // dismiss status dialog
            if (dialog != null)
                dialog.dismiss();

            try {
                finish.run();
            }
            catch (Exception e) {
                // ignored
            }
        }

        public void setFinish(Runnable action) {
            finish = action;
            // TODO syncer.onSyncResumed();
        }
    }


}
