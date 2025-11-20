package com.nutomic.syncthingandroid.fragments

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.activities.MainActivity
import com.nutomic.syncthingandroid.activities.RecentChangesActivity
import com.nutomic.syncthingandroid.activities.SettingsActivity
import com.nutomic.syncthingandroid.activities.TipsAndTricksActivity
import com.nutomic.syncthingandroid.activities.WebGuiActivity
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.service.SyncthingService.OnServiceStateChangeListener
import com.nutomic.syncthingandroid.util.Util

/**
 * Displays information about the local device.
 */
class DrawerFragment : Fragment(), OnServiceStateChangeListener {
    private var mServiceState: SyncthingService.State? = SyncthingService.State.INIT

    private var mDrawerRecentChanges: TextView? = null
    private var mDrawerActionWebGui: TextView? = null
    private var mDrawerActionRestart: TextView? = null

    private var mActivity: MainActivity? = null
    private var sharedPreferences: SharedPreferences? = null

    private var mRunningOnTV = false

    override fun onServiceStateChange(currentState: SyncthingService.State?) {
        mServiceState = currentState
        // Commented out because for now this keeps crashing the app
        //updateUI()
    }

    override fun onResume() {
        super.onResume()
        // Commented out because for now this keeps crashing the app
        //updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * Populates views and menu.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mActivity = activity as MainActivity?
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity!!)
        mRunningOnTV = Util.isRunningOnTV(mActivity)


        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // TODO: add menu header with Syncthing logo

                        // Top navigation items
                        Column {
                            NavigationDrawerItem(
                                label = { Text(text = stringResource(id = R.string.show_device_id)) },
                                icon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                                selected = false,
                                onClick = { showQrCode() }
                            )
                            NavigationDrawerItem(
                                label = { Text(text = stringResource(id = R.string.recent_changes_title)) },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    startActivity(Intent(mActivity, RecentChangesActivity::class.java))
                                    mActivity!!.closeDrawer()
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(text = stringResource(id = R.string.web_gui_title)) },
                                icon = { Icon(Icons.AutoMirrored.Outlined.ViewQuilt, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    startActivity(Intent(mActivity, WebGuiActivity::class.java))
                                    mActivity!!.closeDrawer()
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(text = stringResource(id = R.string.category_backup)) },
                                icon = { Icon(Icons.Default.ImportExport, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    val intent = Intent(mActivity, SettingsActivity::class.java)
                                    intent.putExtra(
                                        SettingsActivity.EXTRA_OPEN_SUB_PREF_SCREEN,
                                        "category_import_export"
                                    )
                                    startActivity(intent)
                                    mActivity!!.closeDrawer()
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(text = stringResource(id = R.string.restart)) },
                                icon = { Icon(Icons.Default.Autorenew, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    mActivity!!.showRestartDialog()
                                    mActivity!!.closeDrawer()
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(text = stringResource(id = R.string.tips_and_tricks_title)) },
                                icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    startActivity(Intent(mActivity, TipsAndTricksActivity::class.java))
                                    mActivity!!.closeDrawer()
                                }
                            )
                        }
                        // Bottom navigation items
                        Column {
                            NavigationDrawerItem(
                                label = { Text(text = stringResource(id = R.string.settings_title)) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    startActivityForResult(
                                        Intent(mActivity, SettingsActivity::class.java),
                                        SETTINGS_SCREEN_REQUEST
                                    )
                                    mActivity!!.closeDrawer()
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(text = stringResource(id = R.string.exit)) },
                                icon = { Icon(Icons.Default.Close, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    if (sharedPreferences != null && sharedPreferences!!.getBoolean(
                                            Constants.PREF_START_SERVICE_ON_BOOT,
                                            false
                                        )
                                    ) {
                                        /**
                                         * App is running as a service. Show an explanation why exiting syncthing is an
                                         * extraordinary request, then ask the user to confirm.
                                         */
                                        AlertDialog.Builder(mActivity!!)
                                            .setTitle(R.string.dialog_exit_while_running_as_service_title)
                                            .setMessage(R.string.dialog_exit_while_running_as_service_message)
                                            .setPositiveButton(
                                                R.string.yes,
                                                DialogInterface.OnClickListener { d: DialogInterface?, i: Int ->
                                                    doExit()
                                                })
                                            .setNegativeButton(
                                                R.string.no,
                                                DialogInterface.OnClickListener { d: DialogInterface?, i: Int -> })
                                            .show()
                                    } else {
                                        // App is not running as a service.
                                        doExit()
                                    }
                                    mActivity!!.closeDrawer()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateUI() {
        val syncthingRunning = mServiceState == SyncthingService.State.ACTIVE

        /**
         * Show Web UI menu item on Android TV for debug builds only.
         * Reason: SyncthingNative's Web UI is not approved by Google because
         * it is lacking full DPAD navigation support.
         */
        mDrawerActionWebGui!!.setVisibility(if (!mRunningOnTV || Constants.isDebuggable(getContext())) View.VISIBLE else View.GONE)

        // Enable buttons if syncthing is running.
        mDrawerRecentChanges!!.setEnabled(syncthingRunning)
        mDrawerActionWebGui!!.setEnabled(syncthingRunning)
        mDrawerActionRestart!!.setEnabled(syncthingRunning)
    }

    /**
     * Gets QRCode and displays it in a Dialog.
     */
    private fun showQrCode() {
        val localDeviceID: String = PreferenceManager.getDefaultSharedPreferences(mActivity!!)
            .getString(com.nutomic.syncthingandroid.service.Constants.PREF_LOCAL_DEVICE_ID, "")!!
        if (TextUtils.isEmpty(localDeviceID)) {
            Toast.makeText(mActivity, R.string.could_not_access_deviceid, Toast.LENGTH_SHORT).show()
            return
        }
        val qrCodeSize = 232
        var qrCodeBitmap: Bitmap? = null
        try {
            qrCodeBitmap = generateQrCodeBitmap(localDeviceID, qrCodeSize, qrCodeSize)
        } catch (ex: WriterException) {
            Log.e(TAG, "showQrCode: generateQrCodeBitmap failed", ex)
        } catch (ex: NullPointerException) {
            Log.e(TAG, "showQrCode: generateQrCodeBitmap failed", ex)
        }
        mActivity!!.showQrCodeDialog(localDeviceID, qrCodeBitmap)
        mActivity!!.closeDrawer()
    }

    @Throws(WriterException::class, NullPointerException::class)
    private fun generateQrCodeBitmap(text: String?, width: Int, height: Int): Bitmap? {
        val bitMatrix: BitMatrix
        try {
            bitMatrix = MultiFormatWriter().encode(
                text, BarcodeFormat.QR_CODE,
                width, height, null
            )
        } catch (ex: IllegalArgumentException) {
            return null
        }
        val bitMatrixWidth = bitMatrix.getWidth()
        val bitMatrixHeight = bitMatrix.getHeight()
        val pixels = IntArray(bitMatrixWidth * bitMatrixHeight)
        val colorWhite = -0x1
        val colorBlack = -0x1000000
        for (y in 0..<bitMatrixHeight) {
            val offset = y * bitMatrixWidth
            for (x in 0..<bitMatrixWidth) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) colorBlack else colorWhite
            }
        }
        val bitmap = Bitmap.createBitmap(bitMatrixWidth, bitMatrixHeight, Bitmap.Config.ARGB_4444)
        bitmap.setPixels(pixels, 0, width, 0, 0, bitMatrixWidth, bitMatrixHeight)
        return bitmap
    }

    private fun doExit(): Boolean {
        if (mActivity == null || mActivity!!.isFinishing()) {
            return false
        }
        Log.i(TAG, "Exiting app on user request")
        mActivity!!.stopService(Intent(mActivity, SyncthingService::class.java))
        mActivity!!.finishAndRemoveTask()
        return true
    }

    /**
     * Receives result of SettingsActivity.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode == SETTINGS_SCREEN_REQUEST && resultCode == SettingsActivity.RESULT_RESTART_APP) {
            Log.d(TAG, "Got request to restart MainActivity")
            if (doExit()) {
                startActivity(Intent(getActivity(), MainActivity::class.java))
            }
        }
    }

    companion object {
        private const val TAG = "DrawerFragment"

        private const val SETTINGS_SCREEN_REQUEST = 3460
    }
}
