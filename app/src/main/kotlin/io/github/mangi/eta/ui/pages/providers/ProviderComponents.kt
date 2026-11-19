package io.github.mangi.eta.ui.pages.providers

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.CustomProviderSetting
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.ui.components.providerBrandLogoRes as sharedProviderBrandLogoRes
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.mangi.eta.ui.components.coui.PreferenceSection

@Composable
internal fun ProviderSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PreferenceSection(title = title, modifier = modifier, content = content)
}

/** 厂商品牌原色图标；资源已经包含适合圆形裁剪的背景与安全区。 */
@Composable
internal fun ProviderBrandIcon(
    sourceType: String,
    modifier: Modifier = Modifier,
) {
    val logo = providerBrandLogoRes(sourceType) ?: return
    ProviderBrandImage(logo = logo, modifier = modifier)
}

@Composable
private fun ProviderBrandImage(
    @DrawableRes logo: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        )
    }
}

@DrawableRes
internal fun providerBrandLogoRes(provider: ProviderSetting): Int? =
    sharedProviderBrandLogoRes(provider)

@DrawableRes
internal fun providerBrandLogoRes(sourceType: String): Int? =
    sharedProviderBrandLogoRes(sourceType)

/** 已知厂商使用品牌图标，未知来源继续按协议类型使用通用图标。 */
@Composable
internal fun ProviderIcon(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
) {
    val logo = providerBrandLogoRes(provider)
    if (logo != null) {
        ProviderBrandImage(logo = logo, modifier = modifier)
        return
    }

    val icon = when (provider) {
        is CustomProviderSetting -> Icons.Rounded.Dns
        else -> Icons.Rounded.Language
    }
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = COUITheme.colorScheme.labelSecondary,
        )
    }
}

internal enum class TagChipTone { Normal, Emphasized }

/** 小胶囊标签，用于能力标签与状态标记。 */
@Composable
internal fun TagChip(
    text: String,
    tone: TagChipTone = TagChipTone.Normal,
) {
    val background: Color
    val foreground: Color
    val weight: FontWeight
    val dark = COUITheme.isDark
    when (tone) {
        TagChipTone.Normal -> {
            background = if (dark) Color(0x1AFFFFFF) else Color(0x0D000000)
            foreground = COUITheme.colorScheme.labelSecondary
            weight = FontWeight.Normal
        }
        TagChipTone.Emphasized -> {
            background = COUITheme.colorScheme.primary.copy(alpha = if (dark) 0.24f else 0.14f)
            foreground = COUITheme.colorScheme.primary
            weight = FontWeight.Medium
        }
    }
    Text(
        text = text,
        style = COUITheme.textStyles.bodyXS,
        fontWeight = weight,
        color = foreground,
        modifier = Modifier
            .squircleSurface(color = background, cornerRadius = 6.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
