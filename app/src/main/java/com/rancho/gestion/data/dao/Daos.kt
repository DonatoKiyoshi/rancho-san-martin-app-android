package com.rancho.gestion.data.dao

import androidx.room.*
import com.rancho.gestion.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios WHERE id_usuario = :id LIMIT 1")
    suspend fun getById(id: String): Usuario?

    @Query("SELECT * FROM usuarios WHERE nombre_usuario = :username LIMIT 1")
    suspend fun getByUsername(username: String): Usuario?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usuario: Usuario)

    @Update
    suspend fun update(usuario: Usuario)

    @Delete
    suspend fun delete(usuario: Usuario)

    @Query("SELECT * FROM usuarios ORDER BY nombre ASC")
    fun getAll(): Flow<List<Usuario>>

    @Query("SELECT * FROM usuarios")
    suspend fun getAllOnce(): List<Usuario>
}

@Dao
interface AnimalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(animal: Animal)

    @Update
    suspend fun update(animal: Animal)

    @Delete
    suspend fun delete(animal: Animal)

    @Query("SELECT * FROM ganado")
    suspend fun getAllOnce(): List<Animal>

    @Query("SELECT * FROM ganado WHERE numero_arete = :arete LIMIT 1")
    suspend fun getByArete(arete: String): Animal?

    @Query("SELECT * FROM ganado WHERE estado_inventario = 'ACTIVO' AND estado_aprobacion = 'APROBADO' ORDER BY fecha_entrada DESC")
    fun getActivos(): Flow<List<Animal>>

    @Query("SELECT * FROM ganado WHERE estado_inventario = 'ACTIVO' AND estado_aprobacion = 'APROBADO' AND numero_arete NOT IN (SELECT numero_arete FROM detalle_venta) ORDER BY fecha_entrada DESC")
    fun getActivosDisponibles(): Flow<List<Animal>>

    @Query("SELECT * FROM ganado WHERE estado_inventario = 'ACTIVO' AND numero_arete LIKE '%' || :query || '%' ORDER BY fecha_entrada DESC")
    fun buscarActivos(query: String): Flow<List<Animal>>

    @Query("SELECT * FROM ganado WHERE estado_inventario != 'ACTIVO' ORDER BY fecha_baja DESC")
    fun getArchivados(): Flow<List<Animal>>

    @Query("SELECT DISTINCT proveedor FROM ganado ORDER BY proveedor")
    fun getProveedores(): Flow<List<String>>

    @Query("SELECT * FROM ganado WHERE estado_aprobacion = 'PENDIENTE' ORDER BY fecha_creacion DESC")
    fun getPendientesAprobacion(): Flow<List<Animal>>

    @Query("SELECT numero_arete FROM ganado WHERE estado_inventario != 'ACTIVO' AND fecha_baja < :limite")
    suspend fun getAntiguosIds(limite: Long): List<String>

    @Query("DELETE FROM ganado WHERE estado_inventario != 'ACTIVO' AND fecha_baja < :limite")
    suspend fun purgarAntiguos(limite: Long): Int
}

@Dao
interface RangoPesoDao {
    @Query("SELECT * FROM rangos_peso ORDER BY limite_inferior ASC")
    fun getAll(): Flow<List<RangoPeso>>

    @Query("SELECT * FROM rangos_peso")
    suspend fun getAllOnce(): List<RangoPeso>

    @Query("SELECT * FROM rangos_peso WHERE id_rango_peso = :id LIMIT 1")
    suspend fun getById(id: String): RangoPeso?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rango: RangoPeso)

    @Delete
    suspend fun delete(rango: RangoPeso)
}

@Dao
interface VentaDao {
    @Transaction
    @Query("SELECT * FROM ventas ORDER BY fecha_venta DESC")
    fun getAllVentas(): Flow<List<Venta>>

    @Query("SELECT * FROM ventas")
    suspend fun getAllVentasOnce(): List<Venta>

    @Query("SELECT * FROM detalle_venta")
    fun getAllDetalles(): Flow<List<DetalleVenta>>

    @Query("SELECT * FROM detalle_venta")
    suspend fun getAllDetallesOnce(): List<DetalleVenta>

    @Query("SELECT * FROM detalle_venta WHERE id_detalle_venta = :id LIMIT 1")
    suspend fun getDetalleById(id: String): DetalleVenta?

    @Query("SELECT * FROM ventas WHERE id_venta = :id LIMIT 1")
    suspend fun getVentaById(id: String): Venta?

    @Query("SELECT * FROM ventas WHERE modalidad = 'JAULA' AND estado_aprobacion = 'PENDIENTE' AND finalizada = 0 ORDER BY fecha_creacion DESC")
    fun getJaulasAbiertas(): Flow<List<Venta>>

    @Query("SELECT * FROM ventas WHERE id_venta IN (SELECT id_venta FROM detalle_venta WHERE numero_arete = :arete) AND estado_aprobacion = 'PENDIENTE' LIMIT 1")
    suspend fun getVentaPendienteDeAnimal(arete: String): Venta?

    @Query("SELECT * FROM ventas WHERE id_venta IN (SELECT id_venta FROM detalle_venta WHERE numero_arete = :arete) AND modalidad = 'JAULA' AND finalizada = 0 LIMIT 1")
    suspend fun getJaulaAbiertaDeAnimal(arete: String): Venta?

    @Query("SELECT id_venta FROM detalle_venta WHERE numero_arete = :arete")
    suspend fun getIdsVentasDeAnimal(arete: String): List<String>

    @Query("SELECT * FROM ventas WHERE finalizada = 1 AND estado_aprobacion = 'PENDIENTE' ORDER BY fecha_actualizacion DESC")
    fun getVentasPendientes(): Flow<List<Venta>>

    @Query("DELETE FROM detalle_venta WHERE numero_arete = :arete AND id_venta IN (SELECT id_venta FROM ventas WHERE estado_aprobacion = 'PENDIENTE')")
    suspend fun eliminarAnimalDeVentasPendientes(arete: String)

    @Query("DELETE FROM ventas WHERE id_venta IN (SELECT id_venta FROM ventas WHERE modalidad = 'INDIVIDUAL' AND estado_aprobacion = 'PENDIENTE' AND id_venta IN (SELECT id_venta FROM detalle_venta WHERE numero_arete = :arete))")
    suspend fun eliminarVentasIndividualesPendientesDeAnimal(arete: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenta(venta: Venta)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetalle(detalle: DetalleVenta)

    @Query("DELETE FROM ventas WHERE id_venta = :ventaId")
    suspend fun deleteVentaById(ventaId: String): Int

    @Query("DELETE FROM detalle_venta WHERE id_venta = :ventaId")
    suspend fun deleteDetallesByVenta(ventaId: String)

    @Update
    suspend fun updateVenta(venta: Venta)

    @Query("SELECT * FROM detalle_venta WHERE id_venta = :ventaId")
    fun getDetallesPorVenta(ventaId: String): Flow<List<DetalleVenta>>

    @Delete
    suspend fun deleteDetalle(detalle: DetalleVenta)

    @Query("SELECT * FROM detalle_venta WHERE numero_arete = :arete LIMIT 1")
    suspend fun getDetalleVentaPorArete(arete: String): DetalleVenta?

    @Query("SELECT * FROM detalle_venta WHERE id_venta = :ventaId")
    suspend fun getDetallesPorVentaOnce(ventaId: String): List<DetalleVenta>

    @Update
    suspend fun updateDetalleVenta(detalle: DetalleVenta)
}

@Dao
interface EgresoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(egreso: Egreso)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNomina(nomina: NominaDetalle)

    @Delete
    suspend fun delete(egreso: Egreso)

    @Query("SELECT * FROM egresos")
    suspend fun getAllOnce(): List<Egreso>

    @Query("SELECT * FROM nomina")
    suspend fun getAllNominaOnce(): List<NominaDetalle>

    @Query("SELECT * FROM nomina WHERE id_nomina = :id LIMIT 1")
    suspend fun getNominaById(id: String): NominaDetalle?

    @Delete
    suspend fun deleteNomina(nomina: NominaDetalle)

    @Query("SELECT * FROM egresos WHERE id_egreso = :id LIMIT 1")
    suspend fun getEgresoById(id: String): Egreso?

    @Query("SELECT MIN(fecha_egreso) FROM egresos")
    fun getPrimeraFechaEgreso(): Flow<Long?>

    @Query("SELECT * FROM egresos WHERE fecha_egreso BETWEEN :desde AND :hasta ORDER BY fecha_egreso DESC")
    fun getEgresosPorRango(desde: Long, hasta: Long): Flow<List<Egreso>>

    @Query("SELECT * FROM egresos WHERE fecha_egreso BETWEEN :desde AND :hasta ORDER BY fecha_egreso DESC")
    suspend fun getEgresosPorRangoOnce(desde: Long, hasta: Long): List<Egreso>

    @Query("SELECT COALESCE(SUM(monto_total), 0) FROM egresos WHERE fecha_egreso BETWEEN :desde AND :hasta")
    fun getTotalPorRango(desde: Long, hasta: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(monto_total), 0) FROM egresos WHERE categoria = :categoria AND fecha_egreso BETWEEN :desde AND :hasta")
    fun getTotalPorCategoria(categoria: CategoriaEgreso, desde: Long, hasta: Long): Flow<Double>
}

@Dao
interface MortalidadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mortalidad: Mortalidad)

    @Query("SELECT * FROM mortalidad")
    suspend fun getAllOnce(): List<Mortalidad>

    @Query("SELECT * FROM mortalidad WHERE numero_arete = :arete AND estado_aprobacion = 'PENDIENTE' LIMIT 1")
    suspend fun getMortalidadPendienteDeAnimal(arete: String): Mortalidad?

    @Query("SELECT * FROM mortalidad WHERE id_mortalidad = :id LIMIT 1")
    suspend fun getMortalidadById(id: String): Mortalidad?

    @Query("DELETE FROM mortalidad WHERE numero_arete = :arete AND estado_aprobacion = 'PENDIENTE'")
    suspend fun eliminarMortalidadesPendientesDeAnimal(arete: String)

    @Query("DELETE FROM mortalidad WHERE numero_arete = :arete AND id_mortalidad != :idAprobado AND estado_aprobacion = 'PENDIENTE'")
    suspend fun eliminarPendientesDeAnimal(arete: String, idAprobado: String)

    @Query("DELETE FROM mortalidad WHERE id_mortalidad = :id")
    suspend fun eliminarSolicitud(id: String)

    @Query("SELECT * FROM mortalidad WHERE estado_aprobacion = 'PENDIENTE' ORDER BY fecha_baja DESC")
    fun getPendientes(): Flow<List<Mortalidad>>

    @Query("SELECT * FROM mortalidad ORDER BY fecha_baja DESC")
    fun getAll(): Flow<List<Mortalidad>>

    @Query("SELECT * FROM mortalidad WHERE numero_arete = :arete AND estado_aprobacion = 'APROBADO' LIMIT 1")
    suspend fun getMortalidadAprobadaPorArete(arete: String): Mortalidad?

    @Update
    suspend fun updateMortalidad(mortalidad: Mortalidad)
}

@Dao
interface ConfiguracionDao {
    @Query("SELECT * FROM configuracion WHERE clave = :clave LIMIT 1")
    suspend fun getByClave(clave: String): Configuracion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: Configuracion)

    @Query("SELECT * FROM configuracion")
    fun getAll(): Flow<List<Configuracion>>
}

@Dao
interface SincronizacionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cola: ColaSincronizacion)

    @Query("SELECT * FROM cola_sincronizacion WHERE estado IN ('PENDIENTE', 'ERROR', 'ENVIANDO') AND numero_intentos < 5 ORDER BY prioridad DESC, fecha_operacion ASC")
    suspend fun getPendientes(): List<ColaSincronizacion>

    @Query("SELECT id_entidad FROM cola_sincronizacion WHERE estado IN ('PENDIENTE', 'ENVIANDO', 'ERROR') AND numero_intentos < 5")
    suspend fun getIdsPendientes(): List<String>

    @Update
    suspend fun update(cola: ColaSincronizacion)

    @Query("DELETE FROM cola_sincronizacion WHERE estado = 'SINCRONIZADO' AND fecha_sincronizacion < :tiempoLimiteMs")
    suspend fun limpiarCola(tiempoLimiteMs: Long)
}

@Dao
interface AuditoriaDao {
    @Insert
    suspend fun insert(auditoria: Auditoria)

    @Query("SELECT * FROM auditoria ORDER BY fecha_operacion DESC LIMIT 100")
    fun getRecientes(): Flow<List<Auditoria>>
}
