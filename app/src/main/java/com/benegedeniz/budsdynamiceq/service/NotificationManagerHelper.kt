package com.benegedeniz.budsdynamiceq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.benegedeniz.budsdynamiceq.MainActivity
import com.benegedeniz.budsdynamiceq.R

class NotificationManagerHelper(private val context: Context) {

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "buds_service_channel",
                context.getString(R.string.app_name_service),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        titleText: String, 
        ruleNcText: String,
        hardwareNcText: String,
        lBatteryText: String,
        rBatteryText: String,
        isLWorn: Boolean,
        isRWorn: Boolean,
        isConnected: Boolean,
        toggleButtonText: String
    ): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent("com.benegedeniz.budsdynamiceq.TOGGLE_NC").apply {
            setPackage(context.packageName)
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            context, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val expandedView = RemoteViews(context.packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.notification_title, titleText)
            setTextViewText(R.id.notification_rule_state, ruleNcText)
            
            if (hardwareNcText.isNotEmpty()) {
                setViewVisibility(R.id.notification_hardware_state, android.view.View.VISIBLE)
                setTextViewText(R.id.notification_hardware_state, hardwareNcText)
            } else {
                setViewVisibility(R.id.notification_hardware_state, android.view.View.GONE)
            }
            
            if (isLWorn) {
                setViewVisibility(R.id.notification_l_container, android.view.View.VISIBLE)
                setTextViewText(R.id.notification_l_status, lBatteryText)
            } else {
                setViewVisibility(R.id.notification_l_container, android.view.View.GONE)
            }
            
            if (isRWorn) {
                setViewVisibility(R.id.notification_r_container, android.view.View.VISIBLE)
                setTextViewText(R.id.notification_r_status, rBatteryText)
            } else {
                setViewVisibility(R.id.notification_r_container, android.view.View.GONE)
            }
            
            setTextViewText(R.id.notification_toggle_button, toggleButtonText)
            setOnClickPendingIntent(R.id.notification_toggle_button, togglePendingIntent)
        }

        val collapsedTitle = if (isConnected) context.getString(R.string.connected) else context.getString(R.string.disconnected)
        val collapsedText = context.getString(R.string.expand_for_more)

        return NotificationCompat.Builder(context, "buds_service_channel")
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setContentTitle(collapsedTitle)
            .setContentText(collapsedText)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expandedView)
            .setOngoing(true)
            .build()
    }

    fun updateNotification(
        titleText: String, 
        ruleNcText: String,
        hardwareNcText: String,
        lBatteryText: String,
        rBatteryText: String,
        isLWorn: Boolean,
        isRWorn: Boolean,
        isConnected: Boolean,
        toggleButtonText: String
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification(titleText, ruleNcText, hardwareNcText, lBatteryText, rBatteryText, isLWorn, isRWorn, isConnected, toggleButtonText))
    }
}
