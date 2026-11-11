package com.ljyh.mei.ui.screen.search

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import com.ljyh.mei.R
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.navigation.MeiloXPinnedListPage
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.rememberPreference
import kotlinx.coroutines.launch

/**
 * 搜索落地页：只保留搜索历史。
 *
 * 上游 MeiloX 在这里放了网易云的「热门推荐」歌单横滑和曲风分类入口，本分叉的
 * 搜索已经改为哔哩哔哩全站搜索，这些入口既无数据也不再可达，因此整体删除。
 */
@Composable
fun SearchLandingScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val historyJson by rememberPreference(SearchHistoryKey, "")
    val history = remember(historyJson) { decodeSearchHistory(historyJson) }
    var showAllHistory by rememberSaveable { mutableStateOf(false) }
    val colors = LocalGlassColors.current

    MeiloXPinnedListPage(
        title = stringResource(R.string.app_tab_search),
        bottomPadding = 0.dp,
        bottomPaddingValues = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        horizontalContentPadding = 0.dp,
        largeTitleHorizontalPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (history.isNotEmpty()) {
            item(key = "search-history") {
                SearchHistorySection(
                    history = if (showAllHistory) history else history.take(5),
                    canExpand = history.size > 5 && !showAllHistory,
                    onExpand = { showAllHistory = true },
                    onSearch = { query ->
                        Screen.SearchResult.navigate(navController) {
                            addPath(Uri.encode(query))
                            addPath("1")
                        }
                    },
                    onRemove = { query -> scope.launch { context.removeSearchHistory(query) } },
                    onClear = { scope.launch { context.clearSearchHistory() } },
                )
            }
        } else {
            item(key = "search-empty-hint") {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_bar_search),
                        style = IosTypography.subheadline,
                        color = colors.secondaryContent,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchHistorySection(
    history: List<String>,
    canExpand: Boolean,
    onExpand: () -> Unit,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalGlassColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.search_history_title), Modifier.weight(1f),
                style = IosTypography.title2, fontWeight = FontWeight.Bold, color = colors.content,
            )
            GlasmioButton(onClick = onClear, style = GlassSurfaceStyle.Navigation) {
                Text(stringResource(R.string.search_history_clear))
            }
        }
        GlasmioGroupedList(Modifier.padding(top = 8.dp)) {
            history.forEachIndexed { index, query ->
                GlasmioListRow(
                    title = query, systemName = "clock", showTopSeparator = index > 0,
                    onClick = { onSearch(query) },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .offset(x = 8.dp)
                                .size(44.dp)
                                .clickable(role = Role.Button, onClick = { onRemove(query) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            SfIcon(
                                SfSymbol.Close,
                                stringResource(R.string.search_history_remove, query),
                                size = 14.dp,
                            )
                        }
                    },
                )
            }
            if (canExpand) GlasmioListRow(
                title = stringResource(R.string.search_history_show_all), onClick = onExpand,
            )
        }
    }
}
