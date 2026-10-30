package com.example.service

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Control Center / Quick Settings TileService for instant "Screen Record to GIF" capture.
 * Allows users to pull down the Android notification shade / Control Center and start or stop
 * GIF screen recording with one tap.
 */
class QuickRecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRecording = ScreenRecordService.recordingState.value.isRecording

        if (isRecording) {
            // Stop active recording and open Editor in MainActivity
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP_AND_EDIT
            }
            runCatching { startService(stopIntent) }
            collapseAndLaunchMainActivity(autoStartRecord = false)
        } else {
            // Collapse Quick Settings panel and launch MainActivity to start recording countdown
            collapseAndLaunchMainActivity(autoStartRecord = true)
        }
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val recState = ScreenRecordService.recordingState.value

        if (recState.isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.qs_tile_stop_gif_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.qs_tile_subtitle_recording)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.qs_tile_record_gif_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.qs_tile_subtitle_ready)
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_screen_record_gif)
        tile.updateTile()
    }

    private fun collapseAndLaunchMainActivity(autoStartRecord: Boolean) {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_START_SCREEN_RECORD, autoStartRecord)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                205,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(activityIntent)
        }
    }

    companion object {
        const val EXTRA_AUTO_START_SCREEN_RECORD = "extra_auto_start_screen_record"

        /**
         * Requests the system to refresh the Quick Settings tile state immediately.
         */
        fun requestTileSync(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, QuickRecordTileService::class.java)
                )
            }
        }

        /**
         * Prompts the user with the native Android 13+ (API 33+) system dialog to add
         * the "录屏转 GIF" tile directly into the Control Center / Quick Settings panel.
         */
        fun requestAddTileToControlCenter(
            context: Context,
            onResultMessage: (String) -> Unit
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                if (statusBarManager == null) {
                    onResultMessage("当前设备状态栏服务不可用，请下拉控制中心手动添加")
                    return
                }
                val componentName = ComponentName(context, QuickRecordTileService::class.java)
                val icon = Icon.createWithResource(context, R.drawable.ic_qs_screen_record_gif)
                val label = context.getString(R.string.qs_tile_record_gif_label)

                runCatching {
                    statusBarManager.requestAddTileService(
                        componentName,
                        label,
                        icon,
                        ContextCompat.getMainExecutor(context)
                    ) { resultCode ->
                        val msg = when (resultCode) {
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                                "已成功将「录屏转 GIF」添加至控制中心"
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                                "「录屏转 GIF」已在控制中心中，下拉状态栏即可使用"
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                                "未添加磁贴，您也可随时下拉控制中心点击编辑添加"
                            else ->
                                "可下拉控制中心点击「编辑」启用「录屏转 GIF」快捷块"
                        }
                        onResultMessage(msg)
                    }
                }.onFailure {
                    onResultMessage("请下拉状态栏控制中心，点击编辑将「录屏转 GIF」拖入快捷栏")
                }
            } else {
                onResultMessage("请下拉状态栏控制中心，点击编辑将「录屏转 GIF」拖入快捷栏")
            }
        }
    }
}
