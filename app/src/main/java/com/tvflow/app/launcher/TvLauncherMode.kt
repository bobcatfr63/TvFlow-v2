package com.tvflow.app.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

data class HomeLauncherStatus(
    val tvFlowAvailable: Boolean,
    val isTvFlowCurrentHome: Boolean,
    val currentHomePackage: String?,
    val availableHomeCount: Int
)

object TvLauncherMode {
    private fun alias(context: Context) =
        ComponentName(context.packageName, context.packageName + ".TvHomeAlias")

    private fun homeIntent() = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)

    fun setEnabled(context: Context, enabled: Boolean): Result<Unit> = runCatching {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            alias(context),
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    fun getStatus(context: Context): HomeLauncherStatus {
        val packageManager = context.packageManager
        val home = homeIntent()
        val homes = packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
        val current = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        val tvFlowAvailable = homes.any { it.activityInfo.packageName == context.packageName }
        val currentPackage = current?.activityInfo?.packageName
        return HomeLauncherStatus(
            tvFlowAvailable = tvFlowAvailable,
            isTvFlowCurrentHome = currentPackage == context.packageName,
            currentHomePackage = currentPackage,
            availableHomeCount = homes.size
        )
    }

    fun openAndroidSettings(context: Context): Result<String> = runCatching {
        val intent = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Réglages Android ouverts"
    }

    fun openHomeChooser(context: Context): Result<String> = runCatching {
        val packageManager = context.packageManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                context.startActivity(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return@runCatching "Sélecteur Android ouvert"
            }
        }

        val settingsIntent = Intent(Settings.ACTION_HOME_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val settingsActivity = packageManager.resolveActivity(
            settingsIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo

        val isUsableHomeSettings = settingsActivity != null &&
            !settingsActivity.name.contains("EmptyStubActivity", ignoreCase = true)

        if (isUsableHomeSettings) {
            context.startActivity(settingsIntent)
            return@runCatching "Réglages de l’écran d’accueil ouverts"
        }

        error("Cette box ne propose pas de sélecteur d'accueil compatible. TVFlow peut toujours démarrer automatiquement. Conservez le launcher Android.")
    }

    fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
