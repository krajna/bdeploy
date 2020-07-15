﻿
using Bdeploy.Launcher.Models;
using Bdeploy.Shared;
using Serilog;
using System;
using System.Diagnostics;
using System.IO;
using System.Text;

namespace Bdeploy.Launcher {
    /// <summary>
    /// Responsible for starting the Java Launcher as well as for applying updates.
    /// </summary>
    public class AppLauncher : BaseLauncher {

        // The full path to the directory where to store updates and backups
        private static readonly string UPDATES = Path.Combine(LAUNCHER, "updates");

        /// Lock file that is created to avoid that multiple launchers install updates simultaneously
        private static readonly string UPDATE_LOCK = Path.Combine(UPDATES, ".lock");

        // The full path of the directory where the launcher stores the next version
        private static readonly string UPDATES_NEXT = Path.Combine(UPDATES, "next");

        // The full path to the directory where we store backups
        private static readonly string BACKUP = Path.Combine(UPDATES, "backup");

        /// <summary>
        /// Event that is raised when installing the update failed.
        /// </summary>
        public event EventHandler<RetryCancelEventArgs> UpdateFailed;

        /// <summary>
        /// Event that is raised when the installer is waiting for another installation to complete.
        /// </summary>
        public event EventHandler<CancelEventArgs> UpdateWaiting;

        /// <summary>
        /// Event that is raised when the update is starting.
        /// </summary>
        public event EventHandler<object> StartUpdating;

        /// <summary>
        /// Creates a new instance of the launcher.
        /// </summary>
        /// <param name="clickAndStartFile">The .bdeploy file to pass to the companion script</param>
        public AppLauncher(string clickAndStartFile) : base(clickAndStartFile) {
        }

        /// <summary>
        /// Starts the LauncherCli in order to launch the application described by the ClickAndStart file.
        /// </summary>
        /// <returns> Exit code of the minion.</returns>
        public int Start() {
            // Descriptor must be existing and valid
            if (!ValidateDescriptor()) {
                return -1;
            }

            // The embedded JRE must be valid
            if (!ValidateEmbeddedJre()) {
                return -2;
            }
            Log.Information("Requesting to start application {0} of instance {1}/{2}", descriptor.ApplicationId, descriptor.GroupId, descriptor.InstanceId);

            // Build arguments to pass to the application
            StringBuilder builder = new StringBuilder();
            AppendCustomJvmArguments(builder);

            // Append classpath and mandatory arguments of application
            builder.AppendFormat("-cp \"{0}\" ", Path.Combine(LIB, "*"));
            builder.AppendFormat("{0} ", MAIN_CLASS);
            builder.AppendFormat("launcher ");
            builder.AppendFormat("\"--launch={0}\" ", clickAndStartFile);
            builder.AppendFormat("\"--updateDir={0}\" ", UPDATES);
            return StartLauncher(builder.ToString());
        }

        /// <summary>
        /// Applies the available updates.
        /// </summary>
        public bool ApplyUpdates() {
            FileStream lockStream = null;
            try {
                // Try to get an exclusive update lock
                lockStream = GetUpdateLock();
                if (lockStream == null) {
                    Log.Information("User canceled installation of updates. Exiting application.");
                    return false;
                }
                // Notify that the update is now starting. waiting screen if we have one
                StartUpdating.Invoke(this, new object());
                Log.Information("Update lock successfully acquired. Starting update. ");

                // Check that we have something to update
                if (!Directory.Exists(UPDATES_NEXT)) {
                    Log.Information("Updates have already been installed. Restarting application.");
                    return true;
                }

                // Backup and update the application
                RetryCancelMode result = RetryCancelMode.RETRY;
                while (result == RetryCancelMode.RETRY) {
                    if (BackupAndUpdate()) {
                        Log.Information("Updates successfully applied. Restarting application.");
                        return true;
                    }
                    RetryCancelEventArgs retryEvent = new RetryCancelEventArgs();
                    UpdateFailed.Invoke(this, retryEvent);
                    result = retryEvent.Result;
                }

                // Backup and update failed. User canceled operation
                Log.Information("Update has been canceled by the user.");
                Log.Information("Restore application from backup.");

                // Copy all files back from the backup to the working directory
                FileHelper.CopyDirectory(BACKUP, LAUNCHER);
                Log.Information("Application restored. Terminating application.");
                return false;
            } finally {
                // Release lock
                if (lockStream != null) {
                    lockStream.Dispose();
                }
                FileHelper.DeleteFile(UPDATE_LOCK);
            }
        }

        /// <summary>
        /// Acquire an exclusive lock to apply updates. 
        /// </summary>
        /// <returns></returns>
        private FileStream GetUpdateLock() {
            // First directly try to get an update lock
            FileStream lockStream = FileHelper.CreateLockFile(UPDATE_LOCK);
            if (lockStream != null) {
                return lockStream;
            }

            // Inform the user that we are waiting for the lock
            Log.Information("Waiting to get exclusive lock to apply the updates.");
            CancelEventArgs cancelEvent = new CancelEventArgs();
            UpdateWaiting.Invoke(this, cancelEvent);
            return FileHelper.WaitForExclusiveLock(UPDATE_LOCK, 500, () => cancelEvent.Result);
        }

        /// <summary>
        /// Creates a backup of the current version and copies the new files back to the working directory.
        /// </summary>
        private bool BackupAndUpdate() {
            try {
                // Cleanup previously created backups
                FileHelper.DeleteDir(BACKUP);
                Directory.CreateDirectory(BACKUP);

                // We first try to check if the JRE is in use before we start moving all files
                if (File.Exists(JRE) && FileHelper.IsFileLocked(JRE)) {
                    Log.Error("JRE executable is locked by another application.");
                    Log.Error("Update cannot be applied.");
                    return false;
                }

                // Move entire launcher into the backup folder
                Log.Information("Moving files to backup directory.");
                FileHelper.MoveDirectory(LAUNCHER, BACKUP, new string[] { "updates" });

                // Copy all files from the update directory to the working directory
                Log.Information("Copy updated files to working directory.");
                FileHelper.CopyDirectory(UPDATES_NEXT, LAUNCHER);

                // Cleanup files in update directory
                Log.Information("Delete update directory.");
                FileHelper.DeleteDir(UPDATES_NEXT);
                return true;
            } catch (Exception ex) {
                Log.Error(ex, "Failed to apply updates.");
                return false;
            }
        }

        /// <summary>
        /// Restarts the launcher application.
        /// </summary>
        public bool Restart() {
            string executable = Process.GetCurrentProcess().MainModule.FileName;
            string argument = string.Format("\"{0}\"", clickAndStartFile);
            Log.Information("Restarting launcher: {0} {1}", executable, argument);

            try {
                int pid = Utils.RunProcess(executable, argument);
                Log.Information("Updated launcher running with PID: {0}", pid);
                return true;
            } catch (Exception ex) {
                Log.Error(ex, "Failed to start launcher after update.");
                Log.Information("Aborting and terminating application.");
                return false;
            }
        }


    }
}