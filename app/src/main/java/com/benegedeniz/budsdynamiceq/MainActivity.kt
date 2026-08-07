package com.benegedeniz.budsdynamiceq

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.benegedeniz.budsdynamiceq.ui.main.MainScreen
import com.benegedeniz.budsdynamiceq.ui.setup.AppIntroScreen
import com.benegedeniz.budsdynamiceq.ui.setup.SetupScreen
import com.benegedeniz.budsdynamiceq.ui.theme.BudsDynamicEQTheme
import com.benegedeniz.budsdynamiceq.util.PermissionManager
import com.benegedeniz.budsdynamiceq.util.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.benegedeniz.budsdynamiceq.util.LanguageUtils.setLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start Foreground Service only if we have a saved device
        val prefs = getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)
        val hasSavedDevice = prefs.getString("TargetDeviceMac", null) != null
        
        if (hasSavedDevice && PermissionManager.hasRequiredPermissions(this)) {
            val serviceIntent = Intent(this, com.benegedeniz.budsdynamiceq.service.BudsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        setContent {
            BudsDynamicEQTheme {
                var permissionsGranted by remember { mutableStateOf(PermissionManager.hasRequiredPermissions(this@MainActivity)) }
                var hasSeenAppIntro by remember { 
                    mutableStateOf(getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE).getBoolean("has_seen_app_intro", false)) 
                }
                val appCoroutineScope = rememberCoroutineScope()
                val appContext = LocalContext.current
                
                LaunchedEffect(Unit) {
                    val versionName = try {
                        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "N/A"
                    } catch (e: Exception) {
                        "N/A"
                    }
                    UpdateChecker.checkForUpdates(versionName, appCoroutineScope)
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsGranted) {
                        if (!hasSeenAppIntro) {
                            AppIntroScreen(
                                onIntroFinished = {
                                    getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE).edit().putBoolean("has_seen_app_intro", true).apply()
                                    hasSeenAppIntro = true
                                }
                            )
                        } else {
                            MainScreen()
                        }
                    } else {
                        SetupScreen(
                            onPermissionsGranted = {
                                permissionsGranted = true
                            }
                        )
                    }
                }
            }
        }
    }
}