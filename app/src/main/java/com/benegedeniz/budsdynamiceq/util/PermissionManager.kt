package com.benegedeniz.budsdynamiceq.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionManager {
    fun hasRequiredPermissions(context: Context): Boolean {
        val prefs = context.getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)
        val hasCompletedSetup = prefs.getBoolean("has_seen_app_intro", false)
        
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (hasCompletedSetup) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ANSWER_PHONE_CALLS
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ANSWER_PHONE_CALLS
                )
            }
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
        }
        val systemPerms = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val notificationPerm = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        return systemPerms && notificationPerm
    }
}
