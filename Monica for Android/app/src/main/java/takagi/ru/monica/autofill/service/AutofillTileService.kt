package takagi.ru.monica.autofill.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import takagi.ru.monica.autofill.AutofillPickerActivityV2

/**
 * Quick Settings Tile for Monica Autofill
 * 
 * Allows users to manually trigger the autofill picker from the notification shade.
 * This is useful when the automatic autofill popup doesn't appear.
 */
class AutofillTileService : TileService() {

    companion object {
        /** Intent Extra: Manual mode flag - indicates the picker was launched from the tile */
        const val EXTRA_MANUAL_MODE = "extra_manual_mode"
    }

    override fun onStartListening() {
        super.onStartListening()
        // Update tile state when the tile becomes visible
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        
        // Create intent to launch the Autofill Picker in "Manual Mode"
        val intent = Intent(applicationContext, AutofillPickerActivityV2::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_MANUAL_MODE, true)
        }
        
        // On Android 14+, we need to use startActivityAndCollapse with PendingIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

