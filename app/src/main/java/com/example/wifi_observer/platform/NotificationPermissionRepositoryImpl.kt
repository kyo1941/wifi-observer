package com.example.wifi_observer.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.wifi_observer.model.NotificationPermissionStatus
import com.example.wifi_observer.platform.interfaces.NotificationPermissionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationPermissionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_permission",
)

class NotificationPermissionRepositoryImpl(
    context: Context,
) : NotificationPermissionRepository {
    companion object {
        private val KEY_PERMISSION_REQUESTED = booleanPreferencesKey("permission_requested")
    }

    private val appContext = context.applicationContext
    private val dataStore = appContext.notificationPermissionDataStore

    override suspend fun getStatus(): NotificationPermissionStatus {
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (notificationsEnabled) {
                NotificationPermissionStatus.NotRequired
            } else {
                NotificationPermissionStatus.RequiredButNotGranted
            }
        }

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            return if (notificationsEnabled) {
                NotificationPermissionStatus.Granted
            } else {
                NotificationPermissionStatus.RequiredButNotGranted
            }
        }

        return if (hasRequestedPermission()) {
            NotificationPermissionStatus.RequiredButNotGranted
        } else {
            NotificationPermissionStatus.Requestable
        }
    }

    override suspend fun recordRequested() {
        dataStore.edit { preferences ->
            preferences[KEY_PERMISSION_REQUESTED] = true
        }
    }

    private suspend fun hasRequestedPermission(): Boolean =
        dataStore.data
            .map { preferences -> preferences[KEY_PERMISSION_REQUESTED] ?: false }
            .first()
}
