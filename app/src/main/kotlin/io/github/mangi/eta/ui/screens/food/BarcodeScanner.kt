package io.github.mangi.eta.ui.screens.food

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.mangi.eta.R
import io.github.mangi.eta.food.BarcodeConfirmation
import io.github.mangi.eta.food.RetailBarcodeFormat
import io.github.mangi.eta.food.isFoodBarcode
import io.github.mangi.eta.food.normalizeRetailBarcode
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.preview.CouiPhonePreviews
import io.github.mangi.eta.ui.preview.CouiPreviewTheme
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

internal fun Context.hasBarcodeCamera(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private enum class ScannerMode { CAMERA, REQUESTING, DENIED, UNAVAILABLE, MANUAL }

@Composable
internal fun BarcodeScanner(startWithManual: Boolean, onClose: () -> Unit, onCode: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var permissionPending by remember { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(startWithManual) }
    var manualCode by rememberSaveable { mutableStateOf("") }
    var cameraFailed by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var torchFailed by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        permissionPending = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasCameraPermission()
    }
    LaunchedEffect(granted, permissionAsked, manual) {
        if (!granted && !permissionAsked && !manual && context.hasBarcodeCamera()) {
            permissionAsked = true
            permissionPending = true
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val torch = camera?.cameraInfo?.takeIf { it.hasFlashUnit() }?.torchState
    DisposableEffect(torch, lifecycleOwner) {
        val observer = Observer<Int> { torchOn = it == TorchState.ON }
        torch?.observe(lifecycleOwner, observer)
        onDispose { torch?.removeObserver(observer) }
    }
    val mode = when {
        manual -> ScannerMode.MANUAL
        !context.hasBarcodeCamera() -> ScannerMode.UNAVAILABLE
        granted -> if (cameraFailed) ScannerMode.UNAVAILABLE else ScannerMode.CAMERA
        permissionPending -> ScannerMode.REQUESTING
        permissionAsked -> ScannerMode.DENIED
        else -> ScannerMode.REQUESTING
    }
    val back = if (manual && !startWithManual) ({ manual = false }) else onClose
    val haptic = LocalHapticFeedback.current
    Dialog(
        onDismissRequest = back,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val isDark = COUITheme.isDark
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
        ScannerScreen(
            mode = mode,
            code = manualCode,
            onClose = onClose,
            onManual = {
                camera = null
                torchOn = false
                torchFailed = false
                manual = true
            },
            onCodeChange = { manualCode = it },
            onSubmit = onCode,
            onSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            },
            onTorch = if (torch != null && mode == ScannerMode.CAMERA) {
                {
                    torchFailed = false
                    val activeCamera = camera
                    activeCamera?.cameraControl?.enableTorch(!torchOn)?.let { future ->
                        future.addListener({
                            if (camera === activeCamera) torchFailed = runCatching { future.get() }.isFailure
                        }, ContextCompat.getMainExecutor(context))
                    }
                }
            } else null,
            torchOn = torchOn,
            torchFailed = torchFailed,
        ) {
            CameraBarcodePreview(
                onCamera = { camera = it },
                onError = {
                    camera = null
                    cameraFailed = true
                },
                onCode = {
                    if (!manual) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onCode(it)
                    }
                },
            )
        }
    }
}

@Composable
private fun ScannerScreen(
    mode: ScannerMode,
    onClose: () -> Unit,
    onManual: () -> Unit,
    onSettings: () -> Unit,
    code: String = "",
    onCodeChange: (String) -> Unit = {},
    onSubmit: (String) -> Unit = {},
    onTorch: (() -> Unit)? = null,
    torchOn: Boolean = false,
    torchFailed: Boolean = false,
    preview: @Composable () -> Unit = {},
) {
    val colors = COUITheme.colorScheme
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.backgroundWithCard,
        topBar = {
            SmallTopAppBar(
                title = stringResource(if (mode == ScannerMode.MANUAL) R.string.food_scan_type else R.string.food_scan_title),
                color = colors.backgroundWithCard,
                navigationIcon = {
                    CouiTopAppBarIconButton(COUIIcons.Basic.Close, stringResource(R.string.food_close), onClose)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .imePadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .then(if (mode == ScannerMode.CAMERA) Modifier else Modifier.verticalScroll(rememberScrollState())),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (mode) {
                ScannerMode.CAMERA -> {
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth().clip(SquircleShape(24.dp)).background(colors.container8),
                        contentAlignment = Alignment.Center,
                    ) {
                        preview()
                        // Two COUI outlines keep the guide visible over both light and dark packaging.
                        Box(
                            Modifier.fillMaxWidth(0.78f).height(minOf(140.dp, maxHeight * 0.4f))
                                .border(2.dp, colors.mask, SquircleShape(12.dp))
                                .padding(2.dp)
                                .border(1.dp, colors.onPrimary, SquircleShape(12.dp)),
                        )
                    }
                    FoodSupporting(stringResource(R.string.food_scan_aim))
                    if (torchFailed) FoodErrorMessage(stringResource(R.string.food_scan_torch_error))
                    if (onTorch != null) {
                        FoodAction(if (torchOn) R.string.food_scan_torch_off else R.string.food_scan_torch_on, onClick = onTorch)
                    }
                }
                ScannerMode.REQUESTING -> FoodLoading()
                ScannerMode.DENIED -> {
                    FoodSection {
                        FoodHeading(stringResource(R.string.food_scan_permission_denied))
                        FoodSupporting(stringResource(R.string.food_scan_permission_detail))
                    }
                    FoodAction(R.string.food_scan_settings, primary = true, onClick = onSettings)
                }
                ScannerMode.UNAVAILABLE -> FoodSection {
                    FoodHeading(stringResource(R.string.food_scan_camera_unavailable))
                    FoodSupporting(stringResource(R.string.food_scan_camera_detail))
                }
                ScannerMode.MANUAL -> {
                    FoodSupporting(stringResource(R.string.food_scan_digits))
                    val trimmed = code.trim()
                    val invalid = trimmed.length > 14 || trimmed.any { it !in '0'..'9' }
                    FoodNumberField(code, onCodeChange, R.string.food_scan_code,
                        errorLabel = R.string.food_scan_invalid.takeIf { invalid })
                    FoodAction(R.string.food_scan_search, primary = true, enabled = trimmed.isFoodBarcode()) { onSubmit(trimmed) }
                }
            }
            if (mode != ScannerMode.MANUAL) {
                FoodAction(R.string.food_scan_type, onClick = onManual)
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraBarcodePreview(onCamera: (Camera) -> Unit, onError: () -> Unit, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val reportCamera by rememberUpdatedState(onCamera)
    val reportError by rememberUpdatedState(onError)
    val reportCode by rememberUpdatedState(onCode)
    val view = remember(context) {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

    DisposableEffect(view, owner) {
        val executor = ContextCompat.getMainExecutor(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val scanner = try {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder().setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E, Barcode.FORMAT_ITF,
                ).build(),
            )
        } catch (_: Exception) {
            reportError()
            return@DisposableEffect onDispose {}
        }
        val confirmation = BarcodeConfirmation()
        var active = true
        var processing = false
        var provider: ProcessCameraProvider? = null
        val lifecycle = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) confirmation.resetFrame()
        }
        owner.lifecycle.addObserver(lifecycle)

        fun stop() {
            if (!active) return
            active = false
            owner.lifecycle.removeObserver(lifecycle)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            if (!processing) scanner.close()
        }

        fun fail() {
            stop()
            reportError()
        }

        analysis.setAnalyzer(executor) { frame ->
            val image = if (active) frame.image else null
            if (image == null) {
                confirmation.resetFrame()
                frame.close()
                return@setAnalyzer
            }
            val task = try {
                scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
            } catch (_: Exception) {
                frame.close()
                fail()
                return@setAnalyzer
            }
            processing = true
            task.addOnCompleteListener(executor) { result ->
                val code = try {
                    if (active && result.isSuccessful && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        confirmation.onFrame(result.result.mapNotNull { it.retailCode() })
                    } else null
                } finally {
                    frame.close()
                    processing = false
                }
                if (!active) {
                    scanner.close()
                    return@addOnCompleteListener
                }
                if (!result.isSuccessful) fail()
                else if (code != null) {
                    stop()
                    reportCode(code)
                }
            }
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (active) {
                try {
                    val cameras = future.get()
                    provider = cameras
                    val selector = if (cameras.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else CameraSelector.DEFAULT_FRONT_CAMERA
                    if (!context.hasCameraPermission()) {
                        fail()
                    } else {
                        reportCamera(cameras.bindToLifecycle(owner, selector, preview, analysis))
                    }
                } catch (_: Exception) {
                    fail()
                }
            }
        }, executor)
        onDispose { stop() }
    }
}

private fun Barcode.retailCode(): String? {
    val format = when (format) {
        Barcode.FORMAT_EAN_8 -> RetailBarcodeFormat.EAN_8
        Barcode.FORMAT_EAN_13 -> RetailBarcodeFormat.EAN_13
        Barcode.FORMAT_UPC_A -> RetailBarcodeFormat.UPC_A
        Barcode.FORMAT_UPC_E -> RetailBarcodeFormat.UPC_E
        Barcode.FORMAT_ITF -> RetailBarcodeFormat.ITF_14
        else -> return null
    }
    return rawValue?.let { normalizeRetailBarcode(it, format) }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun FoodBarcodeScannerPreview() {
    CouiPreviewTheme {
        ScannerScreen(ScannerMode.CAMERA, {}, {}, {}, onTorch = {}) {
            // The headless renderer has no camera surface.
        }
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun FoodBarcodePermissionDeniedPreview() {
    CouiPreviewTheme { ScannerScreen(ScannerMode.DENIED, {}, {}, {}) }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun FoodBarcodeManualPreview() {
    CouiPreviewTheme { ScannerScreen(ScannerMode.MANUAL, {}, {}, {}, code = "2001234000017") }
}
