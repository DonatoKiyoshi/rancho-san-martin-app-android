package com.rancho.gestion.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/* =======================================================================
 *  ENUMS DE NEGOCIO
 * ======================================================================= */

enum class Rol { ADMINISTRADOR, EMPLEADO }

enum class Sexo { MACHO, HEMBRA }

enum class TipoArete { SADER, OTRO }

enum class FaseGanado { ACOPIO, DESARROLLO }

enum class EstadoInventario { ACTIVO, VENDIDO, MUERTO }

enum class EstadoAprobacion { PENDIENTE, APROBADO, RECHAZADO }

enum class ModalidadVenta { INDIVIDUAL, JAULA }

enum class CategoriaEgreso { NOMINA, GASOLINA, ALIMENTO, FARMACOS, OTROS }

enum class TipoBaja { VENTA, MORTALIDAD }

enum class EstadoSincronizacion { PENDIENTE, ENVIANDO, SINCRONIZADO, ERROR }

/* =======================================================================
 *  ENTIDADES (tablas Room según Diccionario de Datos)
 * ======================================================================= */

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_usuario") val idUsuario: Long = 0,
    val nombre: String,
    @ColumnInfo(name = "nombre_usuario") val nombreUsuario: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    val rol: Rol,
    val estado: Boolean = true,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_actualizacion") val fechaActualizacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "ultimo_acceso") val ultimoAcceso: Long? = null,
    val version: Int = 1
)

@Entity(
    tableName = "ganado",
    indices = [Index("id_rango_peso"), Index("estado_inventario"), Index("estado_aprobacion")]
)
data class Animal(
    @PrimaryKey @ColumnInfo(name = "numero_arete") val numeroArete: String, // Validar prefijo 484
    @ColumnInfo(name = "tipo_arete") val tipoArete: TipoArete = TipoArete.SADER,
    val color: String,
    val sexo: Sexo,
    val proveedor: String,
    @ColumnInfo(name = "precio_compra") val precioCompra: Double,
    @ColumnInfo(name = "fecha_entrada") val fechaEntrada: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "peso_entrada") val pesoEntrada: Double,
    @ColumnInfo(name = "id_rango_peso") val idRangoPeso: Long?,
    val fase: FaseGanado = FaseGanado.ACOPIO,
    @ColumnInfo(name = "estado_inventario") val estadoInventario: EstadoInventario = EstadoInventario.ACTIVO,
    @ColumnInfo(name = "estado_aprobacion") val estadoAprobacion: EstadoAprobacion = EstadoAprobacion.PENDIENTE,
    @ColumnInfo(name = "fecha_baja") val fechaBaja: Long? = null,
    @ColumnInfo(name = "tipo_baja") val tipoBaja: TipoBaja? = null,
    @ColumnInfo(name = "fotografia_local") val fotografiaLocal: String? = null,
    @ColumnInfo(name = "fotografia_cloud_key") val fotografiaCloudKey: String? = null,
    @ColumnInfo(name = "fotografia_local_disponible") val fotografiaLocalDisponible: Boolean = false,
    @ColumnInfo(name = "fecha_fotografia_purga") val fechaFotografiaPurga: Long? = null,
    @ColumnInfo(name = "id_usuario_registro") val idUsuarioRegistro: Long,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_actualizacion") val fechaActualizacion: Long = System.currentTimeMillis(),
    val version: Int = 1
)

@Entity(tableName = "rangos_peso")
data class RangoPeso(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_rango_peso") val idRangoPeso: Long = 0,
    @ColumnInfo(name = "limite_inferior") val limiteInferior: Double,
    @ColumnInfo(name = "limite_superior") val limiteSuperior: Double,
    @ColumnInfo(name = "nombre_rango") val nombreRango: String,
    val estado: Boolean = true,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_actualizacion") val fechaActualizacion: Long = System.currentTimeMillis(),
    val version: Int = 1
)

@Entity(tableName = "ventas")
data class Venta(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_venta") val idVenta: Long = 0,
    @ColumnInfo(name = "fecha_venta") val fechaVenta: Long = System.currentTimeMillis(),
    val comprador: String,
    val modalidad: ModalidadVenta,
    @ColumnInfo(name = "monto_total") val montoTotal: Double,
    @ColumnInfo(name = "total_cabezas") val totalCabezas: Int,
    @ColumnInfo(name = "peso_total") val pesoTotal: Double,
    @ColumnInfo(name = "estado_aprobacion") val estadoAprobacion: EstadoAprobacion = EstadoAprobacion.PENDIENTE,
    @ColumnInfo(name = "id_usuario_registro") val idUsuarioRegistro: Long,
    @ColumnInfo(name = "id_usuario_aprobacion") val idUsuarioAprobacion: Long? = null,
    @ColumnInfo(name = "fecha_aprobacion") val fechaAprobacion: Long? = null,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_actualizacion") val fechaActualizacion: Long = System.currentTimeMillis(),
    val finalizada: Boolean = false,
    val version: Int = 1
)

@Entity(
    tableName = "detalle_venta",
    foreignKeys = [
        ForeignKey(
            entity = Venta::class,
            parentColumns = ["id_venta"],
            childColumns = ["id_venta"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("id_venta"), Index("numero_arete")]
)
data class DetalleVenta(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_detalle_venta") val idDetalleVenta: Long = 0,
    @ColumnInfo(name = "id_venta") val idVenta: Long,
    @ColumnInfo(name = "numero_arete") val numeroArete: String,
    @ColumnInfo(name = "peso_salida") val pesoSalida: Double,
    @ColumnInfo(name = "precio_unitario") val precioUnitario: Double,
    val subtotal: Double,
    @ColumnInfo(name = "fecha_registro") val fechaRegistro: Long = System.currentTimeMillis()
)

@Entity(tableName = "egresos")
data class Egreso(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_egreso") val idEgreso: Long = 0,
    @ColumnInfo(name = "fecha_egreso") val fechaEgreso: Long = System.currentTimeMillis(),
    val categoria: CategoriaEgreso,
    val descripcion: String,
    @ColumnInfo(name = "monto_total") val montoTotal: Double,
    @ColumnInfo(name = "id_usuario") val idUsuario: Long,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_actualizacion") val fechaActualizacion: Long = System.currentTimeMillis(),
    val version: Int = 1
)

@Entity(
    tableName = "nomina",
    foreignKeys = [
        ForeignKey(
            entity = Egreso::class,
            parentColumns = ["id_egreso"],
            childColumns = ["id_egreso"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("id_egreso")]
)
data class NominaDetalle(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_nomina") val idNomina: Long = 0,
    @ColumnInfo(name = "id_egreso") val idEgreso: Long,
    @ColumnInfo(name = "nombre_empleado") val nombreEmpleado: String,
    @ColumnInfo(name = "dias_trabajados") val diasTrabajados: Int,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis()
)

@Entity(tableName = "mortalidad")
data class Mortalidad(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_mortalidad") val idMortalidad: Long = 0,
    @ColumnInfo(name = "numero_arete") val numeroArete: String,
    @ColumnInfo(name = "fecha_baja") val fechaBaja: Long = System.currentTimeMillis(),
    val causa: String,
    @ColumnInfo(name = "costo_inicial") val costoInicial: Double,
    val recuperacion: Double,
    @ColumnInfo(name = "merma_economica") val mermaEconomica: Double,
    @ColumnInfo(name = "estado_aprobacion") val estadoAprobacion: EstadoAprobacion = EstadoAprobacion.PENDIENTE,
    @ColumnInfo(name = "id_usuario_registro") val idUsuarioRegistro: Long,
    @ColumnInfo(name = "id_usuario_aprobacion") val idUsuarioAprobacion: Long? = null,
    @ColumnInfo(name = "fecha_aprobacion") val fechaAprobacion: Long? = null,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis(),
    val version: Int = 1
)

@Entity(tableName = "imagenes_animal", indices = [Index("numero_arete")])
data class ImagenAnimal(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_imagen") val idImagen: Long = 0,
    @ColumnInfo(name = "numero_arete") val numeroArete: String,
    @ColumnInfo(name = "ruta_local") val rutaLocal: String,
    @ColumnInfo(name = "cloud_key") val cloudKey: String? = null,
    @ColumnInfo(name = "nombre_archivo") val nombreArchivo: String,
    val formato: String = "webp",
    @ColumnInfo(name = "tamaño_bytes") val tamanoBytes: Long,
    val ancho: Int = 1024,
    val alto: Int = 1024,
    val calidad: Int = 70,
    @ColumnInfo(name = "disponible_local") val disponibleLocal: Boolean = true,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_purga_local") val fechaPurgaLocal: Long? = null,
    @ColumnInfo(name = "fecha_purga_cloud") val fechaPurgaCloud: Long? = null
)

@Entity(tableName = "notificaciones_estancia", indices = [Index("numero_arete"), Index("id_usuario")])
data class NotificacionEstancia(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_notificacion") val idNotificacion: Long = 0,
    @ColumnInfo(name = "numero_arete") val numeroArete: String,
    @ColumnInfo(name = "dias_estancia") val diasEstancia: Int,
    @ColumnInfo(name = "tipo_alerta") val tipoAlerta: String,
    @ColumnInfo(name = "fecha_generacion") val fechaGeneracion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_lectura") val fechaLectura: Long? = null,
    val leida: Boolean = false,
    @ColumnInfo(name = "id_usuario") val idUsuario: Long // Admin destinatario
)

@Entity(tableName = "cola_sincronizacion", indices = [Index("estado")])
data class ColaSincronizacion(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_sincronizacion") val idSincronizacion: Long = 0,
    val entidad: String,
    @ColumnInfo(name = "id_entidad") val idEntidad: String,
    val operacion: String, // INSERT, UPDATE, DELETE
    val estado: EstadoSincronizacion = EstadoSincronizacion.PENDIENTE,
    @ColumnInfo(name = "version_local") val versionLocal: Int,
    @ColumnInfo(name = "fecha_operacion") val fechaOperacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_sincronizacion") val fechaSincronizacion: Long? = null,
    @ColumnInfo(name = "numero_intentos") val numeroIntentos: Int = 0,
    @ColumnInfo(name = "ultimo_error") val ultimoError: String? = null,
    @ColumnInfo(name = "id_dispositivo") val idDispositivo: String,
    val prioridad: Int = 1
)

@Entity(tableName = "dispositivos")
data class Dispositivo(
    @PrimaryKey @ColumnInfo(name = "id_dispositivo") val idDispositivo: String,
    @ColumnInfo(name = "id_usuario") val idUsuario: Long,
    @ColumnInfo(name = "nombre_dispositivo") val nombreDispositivo: String,
    val plataforma: String = "Android",
    @ColumnInfo(name = "version_app") val versionApp: String,
    @ColumnInfo(name = "ultima_sincronizacion") val ultimaSincronizacion: Long? = null,
    val estado: String = "ACTIVO",
    @ColumnInfo(name = "fecha_registro") val fechaRegistro: Long = System.currentTimeMillis()
)

@Entity(tableName = "auditoria", indices = [Index("id_usuario"), Index("entidad")])
data class Auditoria(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id_auditoria") val idAuditoria: Long = 0,
    @ColumnInfo(name = "id_usuario") val idUsuario: Long,
    val entidad: String,
    @ColumnInfo(name = "id_entidad") val idEntidad: String,
    val accion: String,
    @ColumnInfo(name = "datos_anteriores") val datosAnteriores: String? = null,
    @ColumnInfo(name = "datos_nuevos") val datosNuevos: String? = null,
    @ColumnInfo(name = "fecha_operacion") val fechaOperacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "id_dispositivo") val idDispositivo: String
)

@Entity(tableName = "configuracion")
data class Configuracion(
    @PrimaryKey val clave: String,
    val valor: String,
    @ColumnInfo(name = "tipo_dato") val tipoDato: String,
    val descripcion: String,
    @ColumnInfo(name = "fecha_actualizacion") val fechaActualizacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "id_usuario_actualizacion") val idUsuarioActualizacion: Long
)
