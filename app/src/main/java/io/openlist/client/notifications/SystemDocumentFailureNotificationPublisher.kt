package io.openlist.client.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.openlist.client.MainActivity
import io.openlist.client.core.domain.SystemDocumentFailureNotifier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemDocumentFailureNotificationPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemDocumentFailureNotifier {
    override fun notifySaveNeedsAttention(instanceId: String) {
        if (!mayPostNotifications()) return
        createChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SYSTEM_DOCUMENT_FAILURES
            putExtra(EXTRA_INSTANCE_ID, instanceId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            instanceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("OpenList 保存需要处理")
            .setContentText("系统文件保存未确认，请在任务中心处理保留的草稿。")
            .setStyle(NotificationCompat.BigTextStyle().bigText("系统文件保存未确认，请在任务中心处理保留的草稿。"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        NotificationManagerCompat.from(context).notify(instanceId.hashCode(), notification)
    }

    private fun mayPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "系统文件保存",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "提示需要手动处理的系统文件保存草稿"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_OPEN_SYSTEM_DOCUMENT_FAILURES = "io.openlist.client.action.OPEN_SYSTEM_DOCUMENT_FAILURES"
        const val EXTRA_INSTANCE_ID = "io.openlist.client.extra.SYSTEM_DOCUMENT_INSTANCE_ID"
        private const val CHANNEL_ID = "system_document_save_failures"
    }
}
