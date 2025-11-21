package com.nutomic.syncthingandroid.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.annimon.stream.function.Consumer
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.fragments.DeviceListFragment
import com.nutomic.syncthingandroid.fragments.DrawerFragment
import com.nutomic.syncthingandroid.fragments.FolderListFragment
import com.nutomic.syncthingandroid.fragments.StatusFragment
import com.nutomic.syncthingandroid.service.AppPrefs
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.RestApi
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.service.SyncthingService.OnServiceStateChangeListener
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder
import com.nutomic.syncthingandroid.util.PermissionUtil
import com.nutomic.syncthingandroid.util.Util
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.min

/**
 * Shows [FolderListFragment] and
 * [DeviceListFragment] in different tabs, and
 * [DrawerFragment] in the navigation drawer.
 */
class MainActivity : SyncthingActivity(), OnServiceStateChangeListener {

    data class Destination (
        val icon: ImageVector,
        val label: String
    )

    private var ENABLE_VERBOSE_LOG = false

    private var mQrCodeDialog: AlertDialog? = null
    private var mUsageReportingDialog: AlertDialog? = null
    private var mRestartDialog: Dialog? = null

    private var mSyncthingServiceState: SyncthingService.State? = SyncthingService.State.INIT

    private var mViewPager: ViewPager? = null

    private var mFolderListFragment: FolderListFragment? = null
    private var mDeviceListFragment: DeviceListFragment? = null
    private var mStatusFragment: StatusFragment? = null
    private var mDrawerFragment: DrawerFragment? = null

    private var mDrawerToggle: ActionBarDrawerToggle? = null
    private var mDrawerLayout: DrawerLayout? = null
    private var mExitFab: FloatingActionButton? = null

    private var mLastIntent: Intent? = null
    private var oneTimeShot = true

    // Session flag to track if user chose "remind later" for important news
    private var mImportantNewsRemindLaterThisSession = false

    @JvmField
    @Inject
    var mPreferences: SharedPreferences? = null

    private val mUIRefreshHandler = Handler()

    private val mBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (mDrawerLayout!!.isDrawerOpen(GravityCompat.START)) {
                // Close drawer on back button press.
                closeDrawer()
            } else {
                /**
                 * Leave MainActivity in its state as the home button was pressed.
                 * This will avoid waiting for the loading spinner when getting back
                 * and give changes to do UI updates based on EventProcessor in the future.
                 */
                moveTaskToBack(true)
            }
        }
    }

    private val mUIRefreshRunnable: Runnable = object : Runnable {
        override fun run() {
            onTimerEvent()
            mUIRefreshHandler.postDelayed(this, Constants.GUI_UPDATE_INTERVAL)
        }
    }

    /**
     * Handles various dialogs based on current state.
     */
    override fun onServiceStateChange(currentState: SyncthingService.State) {
        mSyncthingServiceState = currentState
        if (oneTimeShot) {
            //updateViewPager()
            oneTimeShot = false
        }

        // Update status light indicating if syncthing is running.
        val btnDisabled = findViewById<View?>(R.id.btnDisabled) as Button?
        val btnStarting = findViewById<View?>(R.id.btnStarting) as Button?
        val btnActive = findViewById<View?>(R.id.btnActive) as Button?
        if (btnDisabled != null && btnStarting != null && btnActive != null) {
            btnActive.setVisibility(if (currentState == SyncthingService.State.ACTIVE) View.VISIBLE else View.GONE)
            btnStarting.setVisibility(if (currentState == SyncthingService.State.STARTING) View.VISIBLE else View.GONE)
            btnDisabled.setVisibility(if (currentState != SyncthingService.State.ACTIVE && currentState != SyncthingService.State.STARTING) View.VISIBLE else View.GONE)
        }

        when (currentState) {
            SyncthingService.State.ACTIVE -> {
                // Check if the usage reporting minimum delay passed by.
                val usageReportingDelayPassed =
                    (Date().getTime() > this.firstStartTime + USAGE_REPORTING_DIALOG_DELAY)
                val restApi = getApi()
                if ((DEBUG_FORCE_USAGE_REPORTING_DIALOG
                            ) || (usageReportingDelayPassed && restApi != null &&
                            restApi.isConfigLoaded() && !restApi.isUsageReportingDecided()
                            )
                ) {
                    showUsageReportingDialog(restApi!!)
                }
            }

            SyncthingService.State.ERROR -> finish()
            // TODO: Kotlin conversion required an else branch for the switch case to be complete. Check what needs to be done here.
            else -> {}
        }
        updateTotalSyncProgressBar()
    }

    private val firstStartTime: Long
        /**
         * Returns the unix timestamp at which the app was first installed.
         */
        get() {
            val pm = getPackageManager()
            var firstInstallTime: Long = 0
            try {
                firstInstallTime = pm.getPackageInfo(getPackageName(), 0).firstInstallTime
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "This should never happen", e)
            }
            return firstInstallTime
        }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainActivityScreen() {
        val context = LocalContext.current
        val syncthingBlue = Color(red = 0, green = 183, blue = 255)

        var destinations = listOf(
            Destination(icon = Icons.Outlined.Folder, label = stringResource(id = R.string.folders_fragment_title)),
            Destination(icon = Icons.Outlined.Devices, label = stringResource(id = R.string.devices_fragment_title)),
            Destination(icon = Icons.Outlined.MonitorHeart, label = stringResource(id = R.string.status_fragment_title))
        )

        var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_syncthing_logo_black),
                                contentDescription = "Syncthing Logo",
                                tint = syncthingBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Syncthing",
                                color = syncthingBlue
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar() {
                    destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            icon = { Icon(destination.icon, contentDescription = null) },
                            onClick = {
                                selectedIndex = index
                                Log.i(TAG,"${destination.label} button pressed")
                            },
                            label = { Text(text = destination.label) }
                        )
                    }
                }
            }
        ) {
        }
    }

    /**
     * Initializes tab navigation.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainActivityScreen()
            }
        }
        (application as SyncthingApp).component().inject(this)
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences)
        if (ENABLE_VERBOSE_LOG) {
            Util.testPathEllipsis()
        }

        return

        //setContentView(R.layout.activity_main)
        //mDrawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        //mViewPager = findViewById<ViewPager>(R.id.pager)


        // Initialize the exit FloatingActionButton
        //mExitFab = findViewById<FloatingActionButton?>(R.id.fabExit)
        //mExitFab!!.setOnClickListener(View.OnClickListener { v: View? -> doExit() })


        // Update FAB visibility based on service configuration
        // TODO: Add exit to menu based on service configuration
        //updateExitFabVisibility()

        val fm = getSupportFragmentManager()
        if (savedInstanceState != null) {
            mFolderListFragment = fm.getFragment(
                savedInstanceState, FolderListFragment::class.java.getName()
            ) as FolderListFragment?
            mDeviceListFragment = fm.getFragment(
                savedInstanceState, DeviceListFragment::class.java.getName()
            ) as DeviceListFragment?
            mStatusFragment = fm.getFragment(
                savedInstanceState, StatusFragment::class.java.getName()
            ) as StatusFragment?
            mDrawerFragment = fm.getFragment(
                savedInstanceState, DrawerFragment::class.java.getName()
            ) as DrawerFragment?
        }
        if (mFolderListFragment == null) {
            mFolderListFragment = FolderListFragment()
        }
        if (mDeviceListFragment == null) {
            mDeviceListFragment = DeviceListFragment()
        }
        if (mStatusFragment == null) {
            mStatusFragment = StatusFragment()
        }
        if (mDrawerFragment == null) {
            mDrawerFragment = DrawerFragment()
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(IS_SHOWING_RESTART_DIALOG)) {
                showRestartDialog()
            }
            if (savedInstanceState.getBoolean(IS_QRCODE_DIALOG_DISPLAYED)) {
                showQrCodeDialog(
                    savedInstanceState.getString(DEVICEID_KEY),
                    savedInstanceState.getParcelable<Bitmap?>(
                        QRCODE_BITMAP_KEY
                    )
                )
            }
        }

        fm.beginTransaction().replace(R.id.drawer, mDrawerFragment!!).commitAllowingStateLoss()
        mDrawerToggle = Toggle(this, mDrawerLayout)
        mDrawerLayout!!.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        mDrawerLayout!!.addDrawerListener(mDrawerToggle!!)
        setOptimalDrawerWidth(findViewById<View?>(R.id.drawer))

        /**
         * SyncthingService needs to be started from this activity as the user
         * can directly launch this activity from the recent activity switcher.
         */
        val serviceIntent = Intent(this, SyncthingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        onNewIntent(getIntent())

        // Register OnBackPressedCallback
        onBackPressedDispatcher.addCallback(this, mBackPressedCallback)
    }


    override fun onNewIntent(intent: Intent) {
        mLastIntent = intent
        super.onNewIntent(intent)
    }


    /**
     * Updates the ViewPager to show tabs depending on the service state.
     */
    /*
    private fun updateViewPager() {
        val numPages = 3
        val mSectionsPagerAdapter: FragmentStatePagerAdapter =
            object : FragmentStatePagerAdapter(getSupportFragmentManager()) {
                override fun getItem(position: Int): Fragment? {
                    when (position) {
                        FOLDER_FRAGMENT_ID -> return mFolderListFragment
                        DEVICE_FRAGMENT_ID -> return mDeviceListFragment
                        STATUS_FRAGMENT_ID -> return mStatusFragment
                        else -> return null
                    }
                }

                override fun getItemPosition(`object`: Any?): Int {
                    return POSITION_NONE
                }

                override fun getCount(): Int {
                    return numPages
                }

                override fun getPageTitle(position: Int): CharSequence? {
                    when (position) {
                        FOLDER_FRAGMENT_ID -> return getResources().getString(R.string.folders_fragment_title)
                        DEVICE_FRAGMENT_ID -> return getResources().getString(R.string.devices_fragment_title)
                        STATUS_FRAGMENT_ID -> return getResources().getString(R.string.status_fragment_title)
                        else -> return position.toString()
                    }
                }
            }
        try {
            mViewPager!!.setAdapter(mSectionsPagerAdapter)
        } catch (e: IllegalStateException) {
            /**
             * IllegalStateException happens due to a bug in FragmentStatePagerAdapter.
             * For more information see:
             * - https://issuetracker.google.com/issues/36956111
             */
            Log.e(TAG, "updateViewPager: IllegalStateException in setAdapter.", e)
            AlertDialog.Builder(this)
                .setMessage(
                    getString(
                        R.string.exception_known_bug_notice,
                        getString(R.string.issue_tracker_url),
                        "108"
                    )
                )
                .setCancelable(false)
                .setPositiveButton(
                    android.R.string.ok,
                    DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> })
                .show()
        }
        val tabLayout = findViewById<TabLayout>(R.id.tabContainer)
        tabLayout.setupWithViewPager(mViewPager)
    }
    */

    public override fun onPause() {
        stopUIRefreshHandler()
        super.onPause()
    }

    /*
    public override fun onResume() {
        // Check if storage permission has been revoked at runtime.
        if (!PermissionUtil.haveStoragePermission(this)) {
            startActivity(Intent(this, FirstStartActivity::class.java))
            this.finish()
            super.onResume()
            return
        }

        // Evaluate run conditions to detect changes made to the metered wifi flags.
        val mSyncthingService = getService()
        if (mSyncthingService != null) {
            mSyncthingService.evaluateRunConditions()
        }

        startUIRefreshHandler()

        // Update FAB visibility in case settings changed
        updateExitFabVisibility()

        val action = mLastIntent!!.getAction()
        if (action != null) {
            if (ACTION_EXIT == action) {
                Log.i(TAG, "Exit app requested by notification action")
                stopService(Intent(this, SyncthingService::class.java))
                finishAndRemoveTask()
            }
        }

        super.onResume()
    }

     */

    public override fun onDestroy() {
        super.onDestroy()
        val mSyncthingService = getService()
        if (mSyncthingService != null) {
            mSyncthingService.unregisterOnServiceStateChangeListener(this)
            mSyncthingService.unregisterOnServiceStateChangeListener(mDrawerFragment)
            mSyncthingService.unregisterOnServiceStateChangeListener(mFolderListFragment)
            mSyncthingService.unregisterOnServiceStateChangeListener(mDeviceListFragment)
            mSyncthingService.unregisterOnServiceStateChangeListener(mStatusFragment)
        }
    }

    /*
    override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder?) {
        super.onServiceConnected(componentName, iBinder)
        val syncthingServiceBinder = iBinder as SyncthingServiceBinder
        val syncthingService = syncthingServiceBinder.getService()
        syncthingService.registerOnServiceStateChangeListener(this)
        syncthingService.registerOnServiceStateChangeListener(mDrawerFragment)
        syncthingService.registerOnServiceStateChangeListener(mFolderListFragment)
        syncthingService.registerOnServiceStateChangeListener(mDeviceListFragment)
        syncthingService.registerOnServiceStateChangeListener(mStatusFragment)
    }

     */

    /**
     * Saves current tab index and fragment states.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val fm = getSupportFragmentManager()
        fm.executePendingTransactions()
        val putFragment = Consumer { fragment: Fragment? ->
            if (fragment != null && fragment.isAdded()) {
                fm.putFragment(outState, fragment.javaClass.getName(), fragment)
            }
        }
        putFragment.accept(mFolderListFragment)
        putFragment.accept(mDeviceListFragment)
        putFragment.accept(mStatusFragment)

        outState.putBoolean(
            IS_SHOWING_RESTART_DIALOG,
            mRestartDialog != null && mRestartDialog!!.isShowing()
        )
        if (mQrCodeDialog != null && mQrCodeDialog!!.isShowing()) {
            outState.putBoolean(IS_QRCODE_DIALOG_DISPLAYED, true)
            val qrCode = mQrCodeDialog!!.findViewById<ImageView?>(R.id.qrcode_image_view)
            val deviceID = mQrCodeDialog!!.findViewById<TextView?>(R.id.device_id)
            outState.putParcelable(
                QRCODE_BITMAP_KEY,
                (qrCode!!.getDrawable() as BitmapDrawable).getBitmap()
            )
            outState.putString(DEVICEID_KEY, deviceID!!.getText().toString())
        }
        Util.dismissDialogSafe(mRestartDialog, this)
        Util.dismissDialogSafe(mUsageReportingDialog, this)
    }

    /*
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val toolbar = findViewById<View?>(R.id.toolbar) as Toolbar?
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.btn_menu)
            toolbar.setNavigationOnClickListener(object : View.OnClickListener {
                override fun onClick(view: View?) {
                    mDrawerLayout!!.openDrawer(GravityCompat.START)
                }
            })
        }

        mDrawerToggle!!.syncState()

        val actionBar = getSupportActionBar()
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true)
        }
    }

     */

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle!!.onConfigurationChanged(newConfig)
    }

    private fun startUIRefreshHandler() {
        LogV("startUIRefreshHandler")
        mUIRefreshHandler.removeCallbacks(mUIRefreshRunnable)
        mUIRefreshHandler.post(mUIRefreshRunnable)
    }

    private fun stopUIRefreshHandler() {
        LogV("stopUIRefreshHandler")
        mUIRefreshHandler.removeCallbacks(mUIRefreshRunnable)
    }

    fun showRestartDialog() {
        mRestartDialog = AlertDialog.Builder(this)
            .setMessage(R.string.dialog_confirm_restart)
            .setPositiveButton(
                android.R.string.yes,
                DialogInterface.OnClickListener { dialogInterface: DialogInterface?, i1: Int ->
                    this.startService(
                        Intent(this, SyncthingService::class.java)
                            .setAction(SyncthingService.ACTION_RESTART)
                    )
                })
            .setNegativeButton(android.R.string.no, null)
            .create()
        mRestartDialog!!.show()
    }

    fun showQrCodeDialog(deviceId: String?, qrCode: Bitmap?) {
        @SuppressLint("InflateParams") val qrCodeDialogView =
            this.getLayoutInflater().inflate(R.layout.dialog_qrcode, null)
        val deviceIdTextView = qrCodeDialogView.findViewById<TextView>(R.id.device_id)
        val shareDeviceIdTextView = qrCodeDialogView.findViewById<TextView>(R.id.actionShareId)
        val qrCodeImageView = qrCodeDialogView.findViewById<ImageView>(R.id.qrcode_image_view)

        deviceIdTextView.setText(deviceId)
        deviceIdTextView.setOnClickListener(View.OnClickListener { v: View? ->
            Util.copyDeviceId(
                this,
                deviceIdTextView.getText().toString()
            )
        })
        shareDeviceIdTextView.setOnClickListener(View.OnClickListener { v: View? ->
            shareDeviceId(
                deviceId
            )
        })
        qrCodeImageView.setImageBitmap(qrCode)

        mQrCodeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.device_id)
            .setView(qrCodeDialogView)
            .setPositiveButton(R.string.finish, null)
            .create()

        mQrCodeDialog!!.show()
    }

    private fun shareDeviceId(deviceId: String?) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.setType("text/plain")
        shareIntent.putExtra(Intent.EXTRA_TEXT, deviceId)
        startActivity(
            Intent.createChooser(
                shareIntent,
                getString(R.string.share_device_id_chooser)
            )
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return mDrawerToggle!!.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)
    }

    /**
     * Handles drawer opened and closed events, toggling option menu state.
     */
    private inner class Toggle(activity: AppCompatActivity?, drawerLayout: DrawerLayout?) :
        ActionBarDrawerToggle(
            activity,
            drawerLayout,
            R.string.open_main_menu,
            R.string.close_main_menu
        ) {
        init {
            setDrawerIndicatorEnabled(false)
        }

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            super.onDrawerSlide(drawerView!!, 0f)
        }
    }

    /**
     * Closes the drawer. Use when navigating away from activity.
     */
    fun closeDrawer() {
        mDrawerLayout!!.closeDrawer(GravityCompat.START)
    }

    /**
     * Toggles the drawer on menu button press.
     */
    override fun onKeyDown(keyCode: Int, e: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!mDrawerLayout!!.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout!!.openDrawer(GravityCompat.START)
            } else {
                closeDrawer()
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && mDrawerLayout!!.isDrawerOpen(
                GravityCompat.START
            )
        ) {
            closeDrawer()
            return true
        }
        return super.onKeyDown(keyCode, e)
    }

    /**
     * Calculating width based on
     * http://www.google.com/design/spec/patterns/navigation-drawer.html#navigation-drawer-specs.
     */
    private fun setOptimalDrawerWidth(drawerContainer: View) {
        var actionBarSize = 0
        val tv = TypedValue()
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarSize =
                TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics())
        }

        val params = drawerContainer.getLayoutParams()
        val displayMetrics = getResources().getDisplayMetrics()
        val minScreenWidth = min(displayMetrics.widthPixels, displayMetrics.heightPixels)

        params.width = min(minScreenWidth - actionBarSize, 5 * actionBarSize)
        drawerContainer.requestLayout()
    }

    /**
     * Displays dialog asking user to accept/deny usage reporting.
     */
    private fun showUsageReportingDialog(restApi: RestApi) {
        Log.v(TAG, "showUsageReportingDialog triggered.")
        val listener = DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
            try {
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        restApi.setUsageReporting(true)
                        restApi.sendConfig()
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                        restApi.setUsageReporting(false)
                        restApi.sendConfig()
                    }

                    DialogInterface.BUTTON_NEUTRAL -> {
                        val intent = Intent(this@MainActivity, WebViewActivity::class.java)
                        intent.putExtra(
                            WebViewActivity.EXTRA_WEB_URL,
                            getString(R.string.syncthing_usage_stats_url)
                        )
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "showUsageReportingDialog:OnClickListener", e)
            }
        }

        restApi.getUsageReport(RestApi.OnResultListener1 { report: String? ->
            @SuppressLint("InflateParams") val v = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.dialog_usage_reporting, null)
            val tv = v.findViewById<TextView>(R.id.example)
            tv.setText(report)
            Util.dismissDialogSafe(mUsageReportingDialog, this@MainActivity)
            mUsageReportingDialog = AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.usage_reporting_dialog_title)
                .setView(v)
                .setPositiveButton(R.string.yes, listener)
                .setNegativeButton(R.string.no, listener)
                .setNeutralButton(R.string.open_website, listener)
                .show()
        })
    }

    // The timer is periodically triggering while the user is looking at the UI.
    private fun onTimerEvent() {
        updateTotalSyncProgressBar()
    }

    @SuppressLint("SetTextI18n")
    private fun updateTotalSyncProgressBar() {
        val topRelTotalSyncProgress = findViewById<View?>(R.id.topRelTotalSyncProgress) as View?
        val pbTotalSyncComplete = findViewById<View?>(R.id.pbTotalSyncComplete) as ProgressBar?
        val tvTotalSyncComplete = findViewById<View?>(R.id.tvTotalSyncComplete) as TextView?
        if (topRelTotalSyncProgress == null || pbTotalSyncComplete == null || tvTotalSyncComplete == null) {
            return
        }

        val restApi = getApi()
        if (restApi == null || !restApi.isConfigLoaded()) {
            topRelTotalSyncProgress.setVisibility(View.GONE)
            return
        }

        val totalSyncCompletePercent = restApi.getTotalSyncCompletion()
        val showTotalSyncProgress = ((mSyncthingServiceState == SyncthingService.State.ACTIVE) &&
                (totalSyncCompletePercent != -1)
                )
        if (!showTotalSyncProgress) {
            topRelTotalSyncProgress.setVisibility(View.GONE)
            return
        }
        topRelTotalSyncProgress.setVisibility(View.VISIBLE)
        pbTotalSyncComplete.setProgress(totalSyncCompletePercent)
        tvTotalSyncComplete.setText(totalSyncCompletePercent.toString())
    }

    /**
     * Shows important news notification if needed.
     * The notification is shown only if the app version has changed since the user last dismissed it
     * and the user hasn't chosen "remind later" in this session.
     */
    private fun showImportantNewsNotificationIfNeeded() {
        val currentVersion = this.currentAppVersion
        val lastDismissedVersion: String = mPreferences?.getString(
            com.nutomic.syncthingandroid.service.Constants.PREF_IMPORTANT_NEWS_SHOWN_VERSION,
            ""
        )!!


        // Show notification if version has changed since last dismissal and user hasn't chosen "remind later" in this session
        if (currentVersion != lastDismissedVersion && !mImportantNewsRemindLaterThisSession) {
            showImportantNewsSnackbar()
        }
    }

    private val currentAppVersion: String
        /**
         * Gets the current app version name.
         */
        get() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName!!
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Failed to get app version", e)
                return ""
            }
        }

    /**
     * Shows the important news notification Snackbar with an action to view options.
     */
    private fun showImportantNewsSnackbar() {
        val rootView = findViewById<View?>(android.R.id.content)
        if (rootView == null) {
            Log.w(TAG, "Cannot show important news Snackbar: root view not found")
            return
        }

        // Show only the title to prevent text truncation
        val snackbar = Snackbar.make(
            rootView,
            getString(R.string.important_news_title),
            Snackbar.LENGTH_INDEFINITE
        )

        // Apply proper theming for better visibility in both light and dark modes
        snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.accent))


        // Get the Snackbar's view and apply background styling
        val snackbarView = snackbar.getView()
        snackbarView.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))


        // Set text color for better contrast
        val textView =
            snackbarView.findViewById<TextView?>(com.google.android.material.R.id.snackbar_text)
        if (textView != null) {
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }

        // Action button to view all options
        snackbar.setAction(
            getString(R.string.important_news_action_view_options),
            View.OnClickListener { v: View? ->
                showImportantNewsActionsDialog()
                snackbar.dismiss()
            })

        snackbar.show()
    }

    /**
     * Shows a dialog with the three important news actions.
     */
    private fun showImportantNewsActionsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.important_news_title)
            .setMessage(
                getString(
                    R.string.important_news_description,
                    getString(R.string.important_news_url)
                )
            )
            .setPositiveButton(
                R.string.important_news_action_open,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    handleImportantNewsAction("open")
                })
            .setNeutralButton(
                R.string.important_news_action_remind,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    handleImportantNewsAction("remind")
                })
            .setNegativeButton(
                R.string.important_news_action_no_remind,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    handleImportantNewsAction("no_remind")
                })
            .show()
    }

    /**
     * Handles the different important news actions.
     */
    private fun handleImportantNewsAction(action: String) {
        Log.v(TAG, "Important news action selected: " + action)

        when (action) {
            "open" -> {
                // Open the important news URL in browser
                val url = getString(R.string.important_news_url)
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
                Log.i(TAG, "User chose to open important news information at: " + url)


                // Save current version so notification won't show again until next app update
                val currentVersion = this.currentAppVersion
                mPreferences!!.edit()
                    .putString(Constants.PREF_IMPORTANT_NEWS_SHOWN_VERSION, currentVersion)
                    .apply()
                Log.i(
                    TAG,
                    "Saved current version " + currentVersion + " to preferences after opening browser"
                )
            }

            "remind" -> {
                // User wants to be reminded later - set session flag to prevent showing again until app restart
                mImportantNewsRemindLaterThisSession = true
                Log.i(
                    TAG,
                    "User chose to be reminded later about important news - will show again on next app start"
                )
            }

            "no_remind" -> {
                // User doesn't want to be reminded anymore for this version
                val currentVersionDismiss = this.currentAppVersion
                mPreferences!!.edit()
                    .putString(Constants.PREF_IMPORTANT_NEWS_SHOWN_VERSION, currentVersionDismiss)
                    .apply()
                Log.i(
                    TAG,
                    "User chose not to be reminded about important news for version " + currentVersionDismiss
                )
            }
        }
    }

    /**
     * Updates the visibility of the exit FAB based on service configuration.
     * FAB is hidden when app is configured to run as a service to avoid confirmation dialogs.
     */
    private fun updateExitFabVisibility() {
        if (mExitFab != null && mPreferences != null) {
            val runAsService =
                mPreferences!!.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false)
            mExitFab!!.setVisibility(if (runAsService) View.GONE else View.VISIBLE)
        }
    }

    /**
     * Exits the application by stopping the service and finishing the activity.
     * This method is called directly from the FAB since it's only visible when safe to exit.
     */
    private fun doExit() {
        if (isFinishing()) {
            return
        }
        Log.i(TAG, "Exiting app on user request via FAB")
        stopService(Intent(this, SyncthingService::class.java))
        finishAndRemoveTask()
    }

    private fun LogV(logMessage: String) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage)
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val IS_SHOWING_RESTART_DIALOG = "RESTART_DIALOG_STATE"
        private const val IS_QRCODE_DIALOG_DISPLAYED = "QRCODE_DIALOG_STATE"
        private const val QRCODE_BITMAP_KEY = "QRCODE_BITMAP"
        private const val DEVICEID_KEY = "DEVICEID"

        private const val FOLDER_FRAGMENT_ID = 0
        private const val DEVICE_FRAGMENT_ID = 1
        private const val STATUS_FRAGMENT_ID = 2

        /**
         * Intent action to exit app.
         */
        const val ACTION_EXIT: String = ".MainActivity.EXIT"

        /**
         * Time after first start when usage reporting dialog should be shown.
         * See [.showUsageReportingDialog]
         */
        private val USAGE_REPORTING_DIALOG_DELAY = TimeUnit.DAYS.toMillis(3)
        private const val DEBUG_FORCE_USAGE_REPORTING_DIALOG = false
    }
}
