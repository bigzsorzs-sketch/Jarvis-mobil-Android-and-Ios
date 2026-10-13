package ai.jarvis.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation

internal val LocalJarvisControls = staticCompositionLocalOf<JarvisControlRegistry?> { null }

@Composable
internal fun RegisterJarvisControl(label: String, enabled: Boolean = true,
    edit: ((String) -> Unit)? = null, click: (() -> Unit)? = null) {
    val registry = LocalJarvisControls.current ?: return
    val id = remember { Any() }
    val translated = JarvisTranslations.text(label, LocalJarvisLanguage.current)
    SideEffect { registry.put(id, JarvisControlRegistry.Control(translated, enabled, edit, click)) }
    DisposableEffect(registry, id) { onDispose { registry.remove(id) } }
}

@Composable
internal fun ControlledButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(), elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null, contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, commandLabel: String,
    content: @Composable RowScope.() -> Unit) {
    RegisterJarvisControl(commandLabel, enabled, click = onClick)
    Button(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
}

@Composable
internal fun ControlledOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(), elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder, contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, commandLabel: String,
    content: @Composable RowScope.() -> Unit) {
    RegisterJarvisControl(commandLabel, enabled, click = onClick)
    OutlinedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
}

@Composable
internal fun ControlledTextButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), elevation: ButtonElevation? = null,
    border: BorderStroke? = null, contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null, commandLabel: String,
    content: @Composable RowScope.() -> Unit) {
    RegisterJarvisControl(commandLabel, enabled, click = onClick)
    TextButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
}

@Composable
internal fun ControlledOutlinedTextField(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current, label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null, leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null, supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false, visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1, commandLabel: String) {
    // Password/key entry remains an Android keyboard operation. Never expose
    // its value or dictate credentials into the conversation transcript.
    if (visualTransformation == VisualTransformation.None)
        RegisterJarvisControl(commandLabel, enabled && !readOnly, edit = onValueChange)
    OutlinedTextField(value, onValueChange, modifier, enabled, readOnly, textStyle,
        label, placeholder, leadingIcon, trailingIcon, supportingText = supportingText,
        isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines)
}

@Composable
internal fun ControlledTab(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, text: @Composable (() -> Unit)? = null, commandLabel: String) {
    RegisterJarvisControl(commandLabel, enabled, click = onClick)
    Tab(selected, onClick, modifier, enabled, text)
}

@Composable
internal fun ControlledFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, commandLabel: String) {
    RegisterJarvisControl(commandLabel, enabled, click = onClick)
    FilterChip(selected, onClick, label, modifier, enabled)
}

@Composable
internal fun ControlledDropdownMenuItem(text: @Composable () -> Unit, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, commandLabel: String) {
    RegisterJarvisControl(commandLabel, enabled, click = onClick)
    DropdownMenuItem(text, onClick, modifier, enabled = enabled)
}
