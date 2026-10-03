package com.sundown.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.icons.SundownIcon
import com.sundown.player.ui.theme.*

/** `.library-search` — glassy inset pill with a magnifier and a clear button. */
@Composable
fun SearchPill(
    value: String,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .height(D.searchHeight)
            .cssShadow(Color(0x4DE0E8EE), blur = 0.dp, offsetY = 1.dp, cornerRadius = D.searchRadius)
            .clip(RoundedCornerShape(D.searchRadius))
            .background(G.search)
            .border(1.dp, P.SearchBorder, RoundedCornerShape(D.searchRadius))
            .innerTopHighlight(Color(0x701F2B35), D.searchRadius)
            .padding(horizontal = D.searchPadH),
    ) {
        SundownIcon(SIcon.Search, D.searchIcon, P.SearchIcon, strokeWidth = 2.7f)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = P.SearchHint,
                        fontSize = 14.cssSp,
                        fontFamily = SundownFontFamily,
                    ),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(P.SearchText),
                textStyle = TextStyle(
                    color = P.SearchText,
                    fontSize = 14.cssSp,
                    fontFamily = SundownFontFamily,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            GhostIconButton(
                icon = SIcon.Close,
                size = 22.dp,
                iconSize = 12.dp,
                tint = androidx.compose.ui.graphics.Color(0xFF788591),
                contentDescription = "Clear search",
                onClick = onClear,
            )
        }
    }
}
