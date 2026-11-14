package com.drivemount.app.ui.settings

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drivemount.app.R
import com.drivemount.app.ui.theme.HsvaColor
import com.drivemount.app.ui.theme.argbToHex
import com.drivemount.app.ui.theme.parseHex
import com.drivemount.app.ui.theme.spacing
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Custom color picker:
 * spectrum (Saturation/Value) + Hue slider + Alpha slider + RGB/HSV মান + HEX।
 * যেকোনো পরিবর্তনে onColorChange কল হয়, তাই অ্যাপের theme লাইভ বদলায়।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorPicker(
    initialArgb: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sp = MaterialTheme.spacing
    var hsva by remember { mutableStateOf(HsvaColor.fromArgb(initialArgb)) }
    var rgbMode by remember { mutableStateOf(true) }

    fun apply(new: HsvaColor) {
        hsva = new
        onColorChange(new.toArgb())
    }

    val argb = hsva.toArgb()
    val opaque = Color(AColor.HSVToColor(floatArrayOf(hsva.h.coerceIn(0f, 359.99f), hsva.s, hsva.v)))
    val hueColors = remember {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 359.99f).map {
            Color(AColor.HSVToColor(floatArrayOf(it, 1f, 1f)))
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = sp.md),
        verticalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        Text(
            text = stringResource(R.string.sheet_custom_title),
            style = MaterialTheme.typography.titleLarge,
        )

        // পূর্বরূপ + HEX
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            ColorSwatch(argb, Modifier.size(56.dp))
            HexField(
                argb = argb,
                onArgb = { apply(HsvaColor.fromArgb(it, hsva)) },
                modifier = Modifier.weight(1f),
            )
        }

        // Spectrum
        SaturationValuePanel(
            hsva = hsva,
            onChange = { s, v -> apply(hsva.copy(s = s, v = v)) },
        )

        // Hue
        Text(
            text = stringResource(R.string.picker_hue),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientSlider(
            fraction = hsva.h / 360f,
            onFraction = { apply(hsva.copy(h = it * 360f)) },
            colors = hueColors,
            thumbColor = hsva.pureHue(),
        )

        // Alpha
        Text(
            text = stringResource(R.string.picker_alpha, (hsva.a * 100f).roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientSlider(
            fraction = hsva.a,
            onFraction = { apply(hsva.copy(a = it)) },
            colors = listOf(opaque.copy(alpha = 0f), opaque),
            thumbColor = opaque,
            checker = true,
        )

        // RGB / HSV
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = rgbMode,
                onClick = { rgbMode = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.picker_rgb)) }
            SegmentedButton(
                selected = !rgbMode,
                onClick = { rgbMode = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.picker_hsv)) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            if (rgbMode) {
                val a = AColor.alpha(argb)
                val r = AColor.red(argb)
                val g = AColor.green(argb)
                val b = AColor.blue(argb)
                NumberField("R", r, 0..255, Modifier.weight(1f)) {
                    apply(HsvaColor.fromArgb(AColor.argb(a, it, g, b), hsva))
                }
                NumberField("G", g, 0..255, Modifier.weight(1f)) {
                    apply(HsvaColor.fromArgb(AColor.argb(a, r, it, b), hsva))
                }
                NumberField("B", b, 0..255, Modifier.weight(1f)) {
                    apply(HsvaColor.fromArgb(AColor.argb(a, r, g, it), hsva))
                }
            } else {
                NumberField("H", hsva.h.roundToInt(), 0..360, Modifier.weight(1f)) {
                    apply(hsva.copy(h = it.toFloat()))
                }
                NumberField("S", (hsva.s * 100f).roundToInt(), 0..100, Modifier.weight(1f)) {
                    apply(hsva.copy(s = it / 100f))
                }
                NumberField("V", (hsva.v * 100f).roundToInt(), 0..100, Modifier.weight(1f)) {
                    apply(hsva.copy(v = it / 100f))
                }
            }
        }
        Spacer(Modifier.height(sp.lg))
    }
}

// ---------------------------------------------------------------------------
// Spectrum
// ---------------------------------------------------------------------------

@Composable
private fun SaturationValuePanel(
    hsva: HsvaColor,
    onChange: (s: Float, v: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val hueColor = hsva.pureHue()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .clip(MaterialTheme.shapes.large)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun report(p: Offset) {
                        currentOnChange(
                            (p.x / size.width).coerceIn(0f, 1f),
                            1f - (p.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                    report(down.position)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            if (it.pressed) {
                                report(it.position)
                                it.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(hsva.s * size.width, (1f - hsva.v) * size.height)
        drawCircle(Color.Black.copy(alpha = 0.3f), radius = 13.5.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White, radius = 12.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
    }
}

// ---------------------------------------------------------------------------
// Hue / Alpha slider
// ---------------------------------------------------------------------------

@Composable
private fun GradientSlider(
    fraction: Float,
    onFraction: (Float) -> Unit,
    colors: List<Color>,
    thumbColor: Color,
    modifier: Modifier = Modifier,
    checker: Boolean = false,
) {
    val currentOnFraction by rememberUpdatedState(onFraction)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val r = size.height / 2f
                    fun report(x: Float) {
                        currentOnFraction(((x - r) / (size.width - 2f * r)).coerceIn(0f, 1f))
                    }
                    report(down.position.x)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            if (it.pressed) {
                                report(it.position.x)
                                it.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val r = size.height / 2f
        val trackHeight = size.height * 0.55f
        val top = (size.height - trackHeight) / 2f
        val track = Path().apply {
            addRoundRect(
                RoundRect(0f, top, size.width, top + trackHeight, CornerRadius(trackHeight / 2f)),
            )
        }
        clipPath(track) {
            if (checker) drawChecker(size, trackHeight / 2f)
            drawRect(Brush.horizontalGradient(colors))
        }

        val cx = r + fraction.coerceIn(0f, 1f) * (size.width - 2f * r)
        val c = Offset(cx, size.height / 2f)
        drawCircle(Color.White, radius = r, center = c)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = r, center = c, style = Stroke(1.dp.toPx()))
        drawCircle(thumbColor, radius = r * 0.7f, center = c)
    }
}

// ---------------------------------------------------------------------------
// Preview swatch (checkerboard-এর ওপর, alpha বোঝার জন্য)
// ---------------------------------------------------------------------------

@Composable
private fun ColorSwatch(argb: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(16.dp))) {
        drawChecker(size, 8.dp.toPx())
        drawRect(Color(argb))
    }
}

private fun DrawScope.drawChecker(area: Size, cell: Float) {
    drawRect(Color.White, size = area)
    val cols = ceil(area.width / cell).toInt()
    val rows = ceil(area.height / cell).toInt()
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            if ((row + col) % 2 == 1) {
                drawRect(
                    color = Color(0xFFCCCCCC),
                    topLeft = Offset(col * cell, row * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Input fields
// ---------------------------------------------------------------------------

@Composable
private fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(3)
            text = digits
            digits.toIntOrNull()?.takeIf { it in range }?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun HexField(
    argb: Int,
    onArgb: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(argb) { mutableStateOf(argbToHex(argb)) }
    var invalid by remember(argb) { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val cleaned = input.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(9)
            text = cleaned
            val parsed = parseHex(cleaned)
            if (parsed != null) {
                invalid = false
                onArgb(parsed)
            } else {
                invalid = true
            }
        },
        label = { Text(stringResource(R.string.picker_hex)) },
        isError = invalid,
        singleLine = true,
        modifier = modifier,
    )
}
