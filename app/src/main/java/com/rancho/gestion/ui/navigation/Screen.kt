package com.rancho.gestion.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Panel : Screen("panel")
    object Ganado : Screen("ganado")
    object RegistrarAnimal : Screen("registrar_animal")
    object Vender : Screen("vender")
    object VentaIndividual : Screen("venta_individual")
    object Jaulas : Screen("jaulas")
    object RegistrarMuerte : Screen("registrar_muerte")
    object Finanzas : Screen("finanzas")
    object ReportesMenu : Screen("reportes_menu")
    object ReportesGanado : Screen("reportes_ganado")
    object ReportesFinanzas : Screen("reportes_finanzas")
    object Configuracion : Screen("configuracion")
    object Aprobaciones : Screen("aprobaciones")
    object Usuarios : Screen("usuarios")
    object DetalleAnimal : Screen("detalle_animal/{arete}") {
        fun createRoute(arete: String) = "detalle_animal/$arete"
    }
}
