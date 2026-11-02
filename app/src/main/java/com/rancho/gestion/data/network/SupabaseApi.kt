package com.rancho.gestion.data.network

import com.rancho.gestion.data.entities.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApi {

    // --- Ganado (Animal) ---
    @POST("ganado") suspend fun insertAnimal(@Body animal: Animal): Response<Unit>
    @PATCH("ganado") suspend fun updateAnimal(@Query("numero_arete") eq: String, @Body animal: Animal): Response<Unit>
    @DELETE("ganado") suspend fun deleteAnimal(@Query("numero_arete") eq: String): Response<Unit>

    // --- Ventas ---
    @POST("ventas") suspend fun insertVenta(@Body venta: Venta): Response<Unit>
    @PATCH("ventas") suspend fun updateVenta(@Query("id_venta") eq: String, @Body venta: Venta): Response<Unit>
    @DELETE("ventas") suspend fun deleteVenta(@Query("id_venta") eq: String): Response<Unit>

    // --- Detalle Venta ---
    @POST("detalle_venta") suspend fun insertDetalleVenta(@Body detalle: DetalleVenta): Response<Unit>
    @PATCH("detalle_venta") suspend fun updateDetalleVenta(@Query("id_detalle_venta") eq: String, @Body detalle: DetalleVenta): Response<Unit>
    @DELETE("detalle_venta") suspend fun deleteDetalleVenta(@Query("id_detalle_venta") eq: String): Response<Unit>

    // --- Egresos ---
    @POST("egresos") suspend fun insertEgreso(@Body egreso: Egreso): Response<Unit>
    @PATCH("egresos") suspend fun updateEgreso(@Query("id_egreso") eq: String, @Body egreso: Egreso): Response<Unit>
    @DELETE("egresos") suspend fun deleteEgreso(@Query("id_egreso") eq: String): Response<Unit>

    // --- Nomina ---
    @POST("nomina") suspend fun insertNomina(@Body nomina: NominaDetalle): Response<Unit>
    @PATCH("nomina") suspend fun updateNomina(@Query("id_nomina") eq: String, @Body nomina: NominaDetalle): Response<Unit>
    @DELETE("nomina") suspend fun deleteNomina(@Query("id_nomina") eq: String): Response<Unit>

    // --- Mortalidad ---
    @POST("mortalidad") suspend fun insertMortalidad(@Body mortalidad: Mortalidad): Response<Unit>
    @PATCH("mortalidad") suspend fun updateMortalidad(@Query("id_mortalidad") eq: String, @Body mortalidad: Mortalidad): Response<Unit>
    @DELETE("mortalidad") suspend fun deleteMortalidad(@Query("id_mortalidad") eq: String): Response<Unit>

    // --- Usuarios ---
    @POST("usuarios") suspend fun insertUsuario(@Body usuario: Usuario): Response<Unit>
    @PATCH("usuarios") suspend fun updateUsuario(@Query("id_usuario") eq: String, @Body usuario: Usuario): Response<Unit>
    @DELETE("usuarios") suspend fun deleteUsuario(@Query("id_usuario") eq: String): Response<Unit>

    // --- Rangos Peso ---
    @POST("rangos_peso") suspend fun insertRangoPeso(@Body rango: RangoPeso): Response<Unit>
    @PATCH("rangos_peso") suspend fun updateRangoPeso(@Query("id_rango_peso") eq: String, @Body rango: RangoPeso): Response<Unit>
    @DELETE("rangos_peso") suspend fun deleteRangoPeso(@Query("id_rango_peso") eq: String): Response<Unit>
}
