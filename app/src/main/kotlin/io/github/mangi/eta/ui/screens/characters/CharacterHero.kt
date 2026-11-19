package io.github.mangi.eta.ui.screens.characters

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.roleplay.CharacterMacros
import io.github.mangi.eta.agent.roleplay.CharacterProfile
import io.github.mangi.eta.ui.components.CharacterAvatar
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.rememberCharacterImage
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.squircle.squircleClip
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

private val HeroAvatarWidth = 132.dp
private val HeroAvatarHeight = HeroAvatarWidth / CharacterPortraitAspect

/**
 * 角色详情主视觉：头像本身放大模糊作为氛围底色并渐隐到页面背景，前景是竖版头像与名字。
 * 模糊只作用在一张已降采样的图上，绘制成本固定，不随页面内容变化。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CharacterHero(
    profile: CharacterProfile,
    busy: Boolean,
    onChangeAvatar: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStart: () -> Unit,
    onReadDescription: () -> Unit,
    userName: String,
) {
    val card = profile.card
    val description = remember(card, userName) { CharacterMacros.expand(card.description, card, userName) }
    val colors = COUITheme.colorScheme
    val image = rememberCharacterImage(profile.avatarPath, HeroAvatarHeight)
    Box(modifier = Modifier.fillMaxWidth()) {
        if (image != null) {
            CharacterAvatar(
                name = card.name,
                avatarPath = profile.avatarPath,
                size = HeroAvatarHeight,
                shape = RectangleShape,
                image = image,
                modifier = Modifier
                    .matchParentSize()
                    .blur(56.dp)
                    .alpha(0.4f),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    // 上下两端都融回页面底色：顶部不在顶栏下切出硬边，底部自然接到下方分组。
                    Brush.verticalGradient(
                        0f to colors.backgroundWithCard,
                        0.18f to colors.backgroundWithCard.copy(alpha = 0f),
                        0.6f to colors.backgroundWithCard.copy(alpha = 0.2f),
                        1f to colors.backgroundWithCard,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CharacterCardPadding)
                .padding(top = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                CharacterAvatar(
                    name = card.name,
                    avatarPath = profile.avatarPath,
                    size = HeroAvatarHeight,
                    shape = RectangleShape,
                    image = image,
                    modifier = Modifier
                        .width(HeroAvatarWidth)
                        .height(HeroAvatarHeight)
                        .squircleClip(CharacterPortraitCorner),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 10.dp, y = 10.dp)
                        .size(36.dp)
                        .squircleSurface(colors.background, 18.dp)
                        .squircleBorder(0.5.dp, colors.outline.copy(alpha = 0.5f), 18.dp)
                        .clickable(enabled = !busy, onClick = onChangeAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "Change avatar",
                        modifier = Modifier.size(18.dp),
                        tint = colors.labelPrimary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.name,
                    style = COUITheme.textStyles.displayXS,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.labelPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(onClick = onToggleFavorite, enabled = !busy, minWidth = 36.dp, minHeight = 36.dp) {
                    Icon(
                        imageVector = if (card.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = if (card.favorite) "Remove favorite" else "Favorite",
                        modifier = Modifier.size(20.dp),
                        tint = if (card.favorite) FavoriteColor else colors.labelSecondary,
                    )
                }
            }
            val byline = listOfNotNull(
                card.creator.takeIf(String::isNotBlank)?.let { "Author $it" },
                card.version.takeIf(String::isNotBlank)?.let { "v$it" },
            ).joinToString(" · ")
            if (byline.isNotEmpty()) {
                Text(
                    text = byline,
                    style = COUITheme.textStyles.bodyS,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxLines = 2,
                ) {
                    card.tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = COUITheme.textStyles.bodyXS,
                            color = colors.labelSecondary,
                            maxLines = 1,
                            modifier = Modifier
                                .squircleSurface(colors.cardPressed, 10.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = description,
                    style = COUITheme.textStyles.bodyM,
                    color = colors.labelPrimary.copy(alpha = 0.86f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .squircleClip(12.dp)
                        .clickable(onClick = onReadDescription)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            CouiButton(
                text = "Start conversation",
                enabled = !busy,
                onClick = onStart,
                role = CouiButtonRole.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 开场白横向翻页：每张卡完整预览一段开场，当前页即选中的开场白，免去单选列表的二次确认。
 * 卡片定高并截断，全文通过“阅读全文”弹窗查看，翻页时高度不跳动。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GreetingPager(
    greetings: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onRead: (Int) -> Unit,
) {
    val colors = COUITheme.colorScheme
    val pagerState = rememberPagerState(initialPage = selected.coerceIn(0, greetings.lastIndex)) { greetings.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onSelect)
    }
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = CharacterCardPadding),
            pageSpacing = 10.dp,
        ) { page ->
            val text = greetings[page]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GreetingCardHeight)
                    .squircleSurface(colors.surfaceWithCard, 20.dp)
                    .clickable(enabled = text.isNotBlank()) { onRead(page) }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = greetingTitle(page),
                        style = COUITheme.textStyles.bodyS,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (greetings.size > 1) {
                        val badge by animateColorAsState(
                            if (page == pagerState.settledPage) colors.primary else Color.Transparent,
                            label = "greeting_selected",
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(badge, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (page == pagerState.settledPage) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(12.dp),
                                    tint = colors.onPrimary,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = text.ifBlank { "No greeting configured. Start the conversation yourself." },
                    style = COUITheme.textStyles.bodyM,
                    color = if (text.isBlank()) colors.labelSecondary else colors.labelPrimary,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (text.isNotBlank()) {
                    Text(
                        text = "Read more",
                        style = COUITheme.textStyles.bodyS,
                        color = colors.primary,
                    )
                }
            }
        }
        if (greetings.size > 1) {
            PagerDots(count = greetings.size, current = pagerState.currentPage)
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int) {
    val colors = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count.coerceAtMost(MaxDots)) { index ->
            val active = index == current.coerceAtMost(MaxDots - 1)
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (active) 16.dp else 6.dp)
                    .background(
                        if (active) colors.primary else colors.labelSecondary.copy(alpha = 0.3f),
                        CircleShape,
                    ),
            )
        }
    }
}

private val GreetingCardHeight = 200.dp
private const val MaxDots = 9
