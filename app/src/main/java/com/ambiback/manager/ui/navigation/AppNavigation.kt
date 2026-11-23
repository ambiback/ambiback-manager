package com.ambiback.manager.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ambiback.manager.data.usb.UsbConnectionState
import com.ambiback.manager.data.usb.UsbSerialManager
import com.ambiback.manager.ui.about.AboutScreen
import com.ambiback.manager.ui.device.DeviceDetailScreen
import com.ambiback.manager.ui.devices.DeviceListScreen
import com.ambiback.manager.ui.provisioning.ProvisioningScreen
import com.ambiback.manager.ui.settings.SettingsScreen
import com.ambiback.manager.ui.usb.UsbDeviceScreen

private object Routes {
    const val DEVICE_LIST   = "deviceList"
    const val DEVICE_DETAIL = "deviceDetail/{deviceIp}?deviceName={deviceName}"
    const val SETTINGS      = "settings"
    const val ABOUT         = "about"
    const val PROVISIONING  = "provisioning"
    const val USB_DEVICE    = "usbDevice?openedByUser={openedByUser}"
    const val USB_DEVICE_BASE = "usbDevice"

    fun usbDevice(openedByUser: Boolean = false) = "usbDevice?openedByUser=$openedByUser"

    fun deviceDetail(deviceIp: String, deviceName: String): String {
        val encodedIp   = Uri.encode(deviceIp)
        val encodedName = Uri.encode(deviceName)
        return "deviceDetail/$encodedIp?deviceName=$encodedName"
    }
}

@Composable
fun AppNavigation(usbSerialManager: UsbSerialManager) {
    val navController = rememberNavController()
    val usbState by usbSerialManager.connectionState.collectAsState()
    val currentRoute by navController.currentBackStackEntryAsState()

    // Auto-navigate to the USB screen when a device connects,
    // and dismiss it when the device disconnects (unless the user opened it manually).
    LaunchedEffect(usbState) {
        when (usbState) {
            is UsbConnectionState.Connected -> {
                if (currentRoute?.destination?.route?.startsWith(Routes.USB_DEVICE_BASE) != true) {
                    navController.navigate(Routes.usbDevice(openedByUser = false)) { launchSingleTop = true }
                }
            }
            is UsbConnectionState.Disconnected -> {
                if (currentRoute?.destination?.route?.startsWith(Routes.USB_DEVICE_BASE) == true) {
                    val openedByUser = currentRoute?.arguments?.getBoolean("openedByUser") ?: false
                    if (!openedByUser) navController.popBackStack()
                }
            }
            else -> Unit
        }
    }

    NavHost(navController = navController, startDestination = Routes.DEVICE_LIST) {

        composable(Routes.DEVICE_LIST) {
            DeviceListScreen(
                onNavigateToDevice   = { deviceIp, deviceName ->
                    navController.navigate(Routes.deviceDetail(deviceIp, deviceName))
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToAbout    = { navController.navigate(Routes.ABOUT) },
                onNavigateToProvisioning = { navController.navigate(Routes.PROVISIONING) },
                onNavigateToUsb      = { navController.navigate(Routes.usbDevice(openedByUser = true)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.PROVISIONING) {
            ProvisioningScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route     = Routes.DEVICE_DETAIL,
            arguments = listOf(
                navArgument("deviceIp")   { type = NavType.StringType },
                navArgument("deviceName") {
                    type = NavType.StringType
                    defaultValue = "Device"
                    nullable = true
                }
            )
        ) {
            DeviceDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route     = Routes.USB_DEVICE,
            arguments = listOf(navArgument("openedByUser") {
                type         = NavType.BoolType
                defaultValue = false
            })
        ) {
            UsbDeviceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
