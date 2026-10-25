package com.rancho.gestion.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rancho.gestion.AppContainer
import com.rancho.gestion.ui.components.AppDrawerContent
import com.rancho.gestion.ui.configuracion.ConfiguracionScreen
import com.rancho.gestion.ui.configuracion.UsuariosScreen
import com.rancho.gestion.ui.finanzas.FinanzasScreen
import com.rancho.gestion.ui.ganado.DetalleAnimalScreen
import com.rancho.gestion.ui.ganado.DetalleAprobacionAnimalScreen
import com.rancho.gestion.ui.ganado.GanadoListScreen
import com.rancho.gestion.ui.ganado.RegistrarAnimalScreen
import com.rancho.gestion.ui.login.LoginScreen
import com.rancho.gestion.ui.panel.PanelScreen
import com.rancho.gestion.ui.reportes.ReportesFinanzasScreen
import com.rancho.gestion.ui.reportes.ReportesGanadoScreen
import com.rancho.gestion.ui.reportes.ReportesMenuScreen
import com.rancho.gestion.ui.vender.VenderContainerScreen
import com.rancho.gestion.ui.vender.VenderMenuScreen
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private val screensWithDrawer = setOf(
    Screen.Panel.route,
    Screen.Ganado.route,
    Screen.Finanzas.route,
    Screen.ReportesMenu.route,
    Screen.Configuracion.route,
    Screen.Aprobaciones.route
)

@Composable
fun RanchoNavHost(container: AppContainer) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.Login.route

    val usuario by SessionManager.usuarioActual.collectAsState()
    val isAdmin = usuario?.let { SessionManager.isAdmin() } ?: false

    val totalPendientes by produceState(initialValue = 0, container, isAdmin) {
        if (isAdmin) {
            combine(
                container.ganadoRepository.pendientesAprobacion(),
                container.ganadoRepository.mortalidadPendiente(),
                container.ventaRepository.getVentasPendientes()
            ) { p1, p2, p3 -> p1.size + p2.size + p3.size }.collect { value = it }
        } else {
            value = 0
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun goTo(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(usuario) {
        if (usuario == null && currentRoute != Screen.Login.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val content: @Composable () -> Unit = {
        NavHost(navController = navController, startDestination = Screen.Login.route) {
            composable(Screen.Login.route) {
                LaunchedEffect(Unit) {
                    container.authRepository.asegurarAdmin()
                }
                LoginScreen(container = container) {
                    val destino = if (SessionManager.isAdmin()) Screen.Panel.route else Screen.Ganado.route
                    navController.navigate(destino) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }
            composable(Screen.Panel.route) {
                if (isAdmin) {
                    PanelScreen(
                        container = container,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onCabezasActivasClick = {
                            navController.navigate(Screen.Ganado.createRoute("Todos"))
                        },
                        onToneladasActivasClick = {
                            navController.navigate(Screen.ReportesGanado.createRoute(periodo = "ANUAL", estado = "ACTIVO"))
                        },
                        onMachosClick = {
                            navController.navigate(Screen.Ganado.createRoute("Macho"))
                        },
                        onHembrasClick = {
                            navController.navigate(Screen.Ganado.createRoute("Hembra"))
                        },
                        onEfectivoGanadoClick = {
                            navController.navigate(Screen.ReportesGanado.createRoute(periodo = "ANUAL", estado = "ACTIVO"))
                        },
                        onEgresosPeriodoClick = {
                            navController.navigate(Screen.Finanzas.route)
                        }
                    )
                } else if (usuario != null) {
                    LaunchedEffect(Unit) { goTo(Screen.Ganado.route) }
                }
            }
            composable(
                route = Screen.Ganado.route,
                arguments = listOf(
                    navArgument("sexo") {
                        type = NavType.StringType
                        defaultValue = "Todos"
                    }
                )
            ) { backStackEntry ->
                val sexo = backStackEntry.arguments?.getString("sexo") ?: "Todos"
                GanadoListScreen(
                    container = container,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onAgregarAnimal = { navController.navigate(Screen.RegistrarAnimal.route) },
                    onAnimalClick = { arete -> 
                        if (isAdmin) {
                            navController.navigate(Screen.DetalleAnimal.createRoute(arete))
                        }
                    },
                    initialSexo = sexo
                )
            }
            composable(Screen.RegistrarAnimal.route) {
                RegistrarAnimalScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(Screen.Vender.route) {
                VenderMenuScreen(
                    onBack = { navController.popBackStack() },
                    onVentaIndividual = { navController.navigate(Screen.VentaIndividual.route) },
                    onJaula = { navController.navigate(Screen.Jaulas.route) },
                    onRegistrarMuerte = { navController.navigate(Screen.RegistrarMuerte.route) }
                )
            }
            composable(Screen.VentaIndividual.route) {
                VenderContainerScreen(container = container, initialPage = 0, onBack = { navController.popBackStack() }, onNavigateToGanado = { goTo(Screen.Ganado.route) })
            }
            composable(Screen.Jaulas.route) {
                VenderContainerScreen(container = container, initialPage = 1, onBack = { navController.popBackStack() }, onNavigateToGanado = { goTo(Screen.Ganado.route) })
            }
            composable(Screen.RegistrarMuerte.route) {
                VenderContainerScreen(container = container, initialPage = 2, onBack = { navController.popBackStack() }, onNavigateToGanado = { goTo(Screen.Ganado.route) })
            }
            composable(Screen.Finanzas.route) {
                if (isAdmin) {
                    FinanzasScreen(
                        container = container,
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    )
                } else if (usuario != null) {
                    LaunchedEffect(Unit) { goTo(Screen.Ganado.route) }
                }
            }
            composable(Screen.ReportesMenu.route) {
                if (isAdmin) {
                    ReportesMenuScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onGanado = { navController.navigate(Screen.ReportesGanado.route) },
                        onFinanzas = { navController.navigate(Screen.ReportesFinanzas.route) }
                    )
                } else if (usuario != null) {
                    LaunchedEffect(Unit) { goTo(Screen.Ganado.route) }
                }
            }
            composable(
                route = Screen.ReportesGanado.route,
                arguments = listOf(
                    navArgument("periodo") {
                        type = NavType.StringType
                        defaultValue = "MENSUAL"
                    },
                    navArgument("estado") {
                        type = NavType.StringType
                        defaultValue = "TODOS"
                    }
                )
            ) { backStackEntry ->
                val periodo = backStackEntry.arguments?.getString("periodo") ?: "MENSUAL"
                val estado = backStackEntry.arguments?.getString("estado") ?: "TODOS"
                if (isAdmin) {
                    ReportesGanadoScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        initialPeriodo = periodo,
                        initialEstado = estado
                    )
                }
            }
            composable(Screen.ReportesFinanzas.route) {
                if (isAdmin) ReportesFinanzasScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(Screen.Configuracion.route) {
                if (usuario != null) {
                    ConfiguracionScreen(
                        container = container,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToUsuarios = { navController.navigate(Screen.Usuarios.route) }
                    )
                }
            }
            composable(Screen.Aprobaciones.route) {
                if (isAdmin) {
                    com.rancho.gestion.ui.components.AprobacionesScreen(
                        container = container,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToDetalle = { arete ->
                            navController.navigate(Screen.DetalleAprobacionAnimal.createRoute(arete))
                        }
                    )
                } else if (usuario != null) {
                    LaunchedEffect(Unit) { goTo(Screen.Ganado.route) }
                }
            }
            composable(Screen.DetalleAprobacionAnimal.route) { backStackEntry ->
                val arete = backStackEntry.arguments?.getString("arete")
                if (arete != null && isAdmin) {
                    DetalleAprobacionAnimalScreen(
                        container = container,
                        arete = arete,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) { goTo(Screen.Aprobaciones.route) }
                }
            }
            composable(Screen.Usuarios.route) {
                if (isAdmin) {
                    UsuariosScreen(container = container, onBack = { navController.popBackStack() })
                } else if (usuario != null) {
                    LaunchedEffect(Unit) { goTo(Screen.Ganado.route) }
                }
            }
            composable(Screen.DetalleAnimal.route) { backStackEntry ->
                val arete = backStackEntry.arguments?.getString("arete")
                if (arete != null && isAdmin) {
                    DetalleAnimalScreen(
                        container = container,
                        arete = arete,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) { goTo(Screen.Ganado.route) }
                }
            }
        }
    }

    if (currentRoute in screensWithDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    currentRoute = currentRoute,
                    isAdmin = isAdmin,
                    usuario = usuario,
                    onNavigate = { route -> goTo(route) },
                    onLogout = { SessionManager.logout() },
                    pendientesCount = totalPendientes
                )
            },
            modifier = Modifier.fillMaxSize(),
            content = { content() }
        )
    } else {
        content()
    }
}
