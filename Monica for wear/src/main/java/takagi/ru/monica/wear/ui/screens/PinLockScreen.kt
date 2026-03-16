package takagi.ru.monica.wear.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import takagi.ru.monica.wear.R
import takagi.ru.monica.wear.ui.components.ExpressiveBackground
import takagi.ru.monica.wear.ui.components.MonicaTimeText

@Composable
fun PinLockScreen(
    isFirstTime: Boolean,
    onPinEntered: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val shakeAnimation = remember { Animatable(0f) }
    val pinFormatError = stringResource(R.string.pin_format_error)
    val pinMismatchError = stringResource(R.string.pin_error_subtitle)

    val inputValue = if (isConfirming) confirmPin else pin
    val titleText = when {
        errorMessage == pinMismatchError && isFirstTime -> stringResource(R.string.pin_mismatch_title)
        isFirstTime && !isConfirming -> stringResource(R.string.pin_set_title)
        isFirstTime -> stringResource(R.string.pin_confirm_title)
        else -> stringResource(R.string.pin_enter_title)
    }
    val subtitleText = when {
        errorMessage != null -> errorMessage!!
        isFirstTime && !isConfirming -> stringResource(R.string.pin_set_subtitle)
        isFirstTime -> stringResource(R.string.pin_confirm_subtitle)
        else -> stringResource(R.string.pin_keyboard_hint)
    }

    LaunchedEffect(isConfirming) {
        delay(180)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            repeat(3) {
                shakeAnimation.animateTo(10f, tween(45))
                shakeAnimation.animateTo(-10f, tween(45))
            }
            shakeAnimation.animateTo(0f, tween(45))
        }
    }

    ScreenScaffold(
        timeText = { MonicaTimeText() }
    ) { contentPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            ExpressiveBackground()

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .verticalScroll(scrollState)
                    .imePadding()
                    .graphicsLayer { translationX = shakeAnimation.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                BasicTextField(
                    value = inputValue,
                    onValueChange = { value ->
                        val digitsOnly = value.filter { it.isDigit() }.take(6)
                        errorMessage = null
                        if (isConfirming) {
                            confirmPin = digitsOnly
                        } else {
                            pin = digitsOnly
                        }

                        if (digitsOnly.length == 6) {
                            keyboardController?.hide()
                            handlePinSubmit(
                                isFirstTime = isFirstTime,
                                isConfirming = isConfirming,
                                pin = if (isConfirming) pin else digitsOnly,
                                confirmPin = if (isConfirming) digitsOnly else confirmPin,
                                currentValue = digitsOnly,
                                onSetConfirming = { isConfirming = it },
                                onClearPins = {
                                    pin = ""
                                    confirmPin = ""
                                },
                                onError = { errorMessage = it },
                                onSuccess = onPinEntered,
                                formatError = pinFormatError,
                                mismatchError = pinMismatchError
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .padding(horizontal = 4.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            handlePinSubmit(
                                isFirstTime = isFirstTime,
                                isConfirming = isConfirming,
                                pin = pin,
                                confirmPin = confirmPin,
                                currentValue = inputValue,
                                onSetConfirming = { isConfirming = it },
                                onClearPins = {
                                    pin = ""
                                    confirmPin = ""
                                },
                                onError = { errorMessage = it },
                                onSuccess = onPinEntered,
                                formatError = pinFormatError,
                                mismatchError = pinMismatchError
                            )
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (inputValue.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.pin_input_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(
                                        color = if (errorMessage != null) {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                                        },
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${inputValue.length}/6",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

private fun handlePinSubmit(
    isFirstTime: Boolean,
    isConfirming: Boolean,
    pin: String,
    confirmPin: String,
    currentValue: String,
    onSetConfirming: (Boolean) -> Unit,
    onClearPins: () -> Unit,
    onError: (String?) -> Unit,
    onSuccess: (String) -> Unit,
    formatError: String,
    mismatchError: String
) {
    val normalized = currentValue.filter { it.isDigit() }
    if (normalized.length != 6) {
        onError(formatError)
        return
    }

    if (isFirstTime) {
        if (!isConfirming) {
            onError(null)
            onSetConfirming(true)
            return
        }

        if (pin == confirmPin) {
            onError(null)
            onSuccess(pin)
        } else {
            onClearPins()
            onSetConfirming(false)
            onError(mismatchError)
        }
        return
    }

    onError(null)
    onSuccess(normalized)
}
