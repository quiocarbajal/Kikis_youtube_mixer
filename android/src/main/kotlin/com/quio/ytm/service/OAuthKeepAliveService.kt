package com.quio.ytm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class OAuthKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "ytm_auth_channel"
        private const val NOTIFICATION_ID = 8888

        fun start(context: Context) {
            try {
                val intent = Intent(context, OAuthKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("OAuthKeepAliveService", "Failed to start keep-alive service", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, OAuthKeepAliveService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                android.util.Log.e("OAuthKeepAliveService", "Failed to stop keep-alive service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Autenticación YouTube Music",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activa la conexión mientras te identificas en el navegador"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Conectando con YouTube Music...")
            .setContentText("Esperando confirmación en el navegador...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
