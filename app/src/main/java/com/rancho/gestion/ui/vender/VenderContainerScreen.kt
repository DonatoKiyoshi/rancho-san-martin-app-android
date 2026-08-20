package com.rancho.gestion.ui.vender

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.rancho.gestion.AppContainer
import com.rancho.gestion.ui.components.BackTopBar
import kotlinx.coroutines.launch

@Composable
fun VenderContainerScreen(
    container: AppContainer,
    initialPage: Int = 0,
    onBack: () -> Unit,
    onNavigateToGanado: () -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { 3 }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val titles = listOf("Individual", "Jaulas", "Muerte")

    // We track the active page to notify children and close scanners
    val activePage by remember { derivedStateOf { pagerState.currentPage } }

    Scaffold(
        topBar = {
            Column {
                BackTopBar(
                    title = "Vender - ${titles[activePage]}",
                    onBack = onBack
                )
                TabRow(
                    selectedTabIndex = activePage,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            selected = activePage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            beyondViewportPageCount = 0,
            userScrollEnabled = true
        ) { page ->
            // Use key to force full recomposition/reset when page changes
            // and pass activePage to child to handle scanner auto-close
            key(page) {
                when (page) {
                    0 -> VentaIndividualContent(container, isActive = activePage == 0, onSalirToGanado = onNavigateToGanado)
                    1 -> JaulasContent(container, isActive = activePage == 1, snackbarHostState = snackbarHostState)
                    2 -> RegistrarMuerteContent(container, isActive = activePage == 2, onSalirToGanado = onNavigateToGanado)
                }
            }
        }
    }
}
