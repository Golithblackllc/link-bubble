package com.linkbubble.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import com.google.android.hotword.client.HotwordServiceClient;
import com.linkbubble.BuildConfig;
import com.linkbubble.Constant;
import com.linkbubble.MainApplication;
import com.linkbubble.MainController;
import com.linkbubble.R;
import com.linkbubble.Settings;
import com.linkbubble.util.CrashTracking;
import com.squareup.otto.Subscribe;

import java.util.List;

public class ExpandedActivity extends Activity {

    private static final String TAG = "ExpandedActivity";
    
    private boolean mIsAlive = false;
    private boolean mIsShowing = false;
    private final Handler mHandler = new Handler();

    private HotwordServiceClient mHotwordServiceClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "MainActivity.onCreate()");

        super.onCreate(savedInstanceState);
        CrashTracking.init(this);

        mIsAlive = true;

        setContentView(R.layout.activity_expanded);

        getActionBar().hide();

        MainApplication.registerForBus(this, this);

        if (Constant.EXPANDED_ACTIVITY_DEBUG) {
            View view = findViewById(R.id.expanded_root);
            view.setBackgroundColor(0x5500ff00);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Settings.get().getOkGooglePreference()) {
            try {
                mHotwordServiceClient = new HotwordServiceClient(this);
            } catch (Exception ex) {
                // ignore if anything went wrong...
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        Log.d(TAG, "onAttachedToWindow()");

        if (mHotwordServiceClient != null) {
            mHotwordServiceClient.onAttachedToWindow();
            mHotwordServiceClient.requestHotwordDetection(true);
        }
    }

    @Override
    public void onDetachedFromWindow() {

        Log.d(TAG, "onDetachedFromWindow()");

        if (mHotwordServiceClient != null) {
            mHotwordServiceClient.onDetachedFromWindow();
            mHotwordServiceClient.requestHotwordDetection(false);
        }

        super.onDetachedFromWindow();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onBeginCollapseTransition(MainController.BeginCollapseTransitionEvent e) {
        minimize();
    }

    /*
     * Get the most recent RecentTaskInfo, but ensure the result is not Link Bubble.
     */
    ActivityManager.RecentTaskInfo getPreviousTaskInfo(List<ActivityManager.RecentTaskInfo> recentTasks) {
        for (int i = 0; i < recentTasks.size(); i++) {
            ActivityManager.RecentTaskInfo recentTaskInfo = recentTasks.get(i);
            if (recentTaskInfo.baseIntent != null
                    && recentTaskInfo.baseIntent.getComponent() != null) {
                String packageName = recentTaskInfo.baseIntent.getComponent().getPackageName();
                if (packageName.equals("android") == false && packageName.equals(BuildConfig.PACKAGE_NAME) == false) {
                    return recentTaskInfo;
                }
            }
        }

        return null;
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "MainActivity.onDestroy()");

        super.onDestroy();

        mIsAlive = false;

        MainApplication.unregisterForBus(this, this);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "MainActivity.onStart()");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "MainActivity.onStop()");
        super.onStop();

        //mTopMaskImage.setVisibility(View.GONE);
        //mBottomMaskImage.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "MainActivity.onResume()");
        super.onResume();

        mIsShowing = true;

        MainController mainController = MainController.get();
        if (mainController == null) {
            minimize();
        }

        if (mHotwordServiceClient != null) {
            mHotwordServiceClient.requestHotwordDetection(true);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "MainActivity.onPause()");
        super.onPause();
        mIsShowing = false;

        if (mHotwordServiceClient != null) {
            mHotwordServiceClient.requestHotwordDetection(false);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        delayedMinimize();
    }

    void minimize() {
        if (mIsAlive == false) {
            return;
        }

        if (mIsShowing == false) {
            Log.d(TAG, "minimize() - mIsShowing:" + mIsShowing + ", exiting...");
            return;
        }

        if (moveTaskToBack(true)) {
            Log.d(TAG, "minimize() - moveTaskToBack(true);");
            return;
        }

        MainController.get().showBadge(true);

        final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RecentTaskInfo> recentTasks = activityManager.getRecentTasks(16, ActivityManager.RECENT_WITH_EXCLUDED);

        if (recentTasks.size() > 0) {
            ActivityManager.RecentTaskInfo recentTaskInfo = getPreviousTaskInfo(recentTasks);
            ComponentName componentName = recentTaskInfo.baseIntent.getComponent();
            //openedFromAppName = componentName.getPackageName();
            Intent intent = new Intent();
            intent.setComponent(componentName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            overridePendingTransition(0, 0);
            Log.e(TAG, "minimize() - " + componentName);
        } else {
            Log.e(TAG, "minimize() - NONE!");
        }
    }

    void delayedMinimize() {
        // Kill the activity to ensure it is not alive in the event a link is intercepted,
        // thus displaying the ugly UI for a few frames
        //fadeOut();
        mHandler.postDelayed(mMinimizeRunnable, 500);
    }

    void cancelDelayedMinimize() {
        //fadeIn();
        mHandler.removeCallbacks(mMinimizeRunnable);
    }

    private Runnable mMinimizeRunnable = new Runnable() {
        @Override
        public void run() {
            minimize();
        }
    };

}