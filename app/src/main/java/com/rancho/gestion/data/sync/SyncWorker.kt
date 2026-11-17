package com.rancho.gestion.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rancho.gestion.data.AppDatabase
import com.rancho.gestion.data.entities.EstadoSincronizacion
import com.rancho.gestion.data.network.SupabaseApi
import com.rancho.gestion.data.network.SupabaseClient
import com.rancho.gestion.data.network.SupabaseStorageApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("SyncWorker", "Iniciando sincronización con Supabase...")

        val database = AppDatabase.getInstance(applicationContext)
        val syncDao = database.sincronizacionDao()
        val api = SupabaseClient.retrofit.create(SupabaseApi::class.java)
        val storageApi = SupabaseClient.storageRetrofit.create(SupabaseStorageApi::class.java)

        val photosDir = File(applicationContext.filesDir, "animal_photos").apply {
            if (!exists()) mkdirs()
        }

        // =====================================================================
        // FASE 1: DESCARGA (PULL) - De Supabase hacia la base de datos local
        // =====================================================================
        val pendingIds = try {
            syncDao.getIdsPendientes().toSet()
        } catch (_: Exception) {
            emptySet()
        }

        try {
            // 1. Usuarios
            val respUsuarios = api.getUsuarios()
            if (respUsuarios.isSuccessful) {
                respUsuarios.body()?.forEach { usuario ->
                    database.usuarioDao().insert(usuario)
                }
            }

            // 2. Rangos de Peso
            val respRangos = api.getRangosPeso()
            if (respRangos.isSuccessful) {
                respRangos.body()?.forEach { rango ->
                    database.rangoPesoDao().insert(rango)
                }
            }

            // 3. Ganado y sus fotografías
            val respGanado = api.getGanado()
            if (respGanado.isSuccessful) {
                respGanado.body()?.forEach { animal ->
                    if (animal.numeroArete !in pendingIds) {
                        var localFotoPath = animal.fotografiaLocal
                        var fotoDisponible = animal.fotografiaLocalDisponible

                        // Si tiene fotografia en la nube (fotografiaCloudKey) y no la tenemos localmente
                        val cloudKey = animal.fotografiaCloudKey
                        if (!cloudKey.isNullOrBlank()) {
                            val localTarget = File(photosDir, cloudKey)
                            if (localTarget.exists() && localTarget.length() > 0) {
                                localFotoPath = localTarget.absolutePath
                                fotoDisponible = true
                            } else {
                                // Descargar imagen desde Supabase Storage
                                try {
                                    val dlResp = storageApi.downloadPublicObject(
                                        bucket = SupabaseClient.BUCKET_ANIMALES,
                                        path = cloudKey
                                    )
                                    if (dlResp.isSuccessful && dlResp.body() != null) {
                                        val bytes = dlResp.body()!!.bytes()
                                        FileOutputStream(localTarget).use { fos ->
                                            fos.write(bytes)
                                        }
                                        localFotoPath = localTarget.absolutePath
                                        fotoDisponible = true
                                        Log.d("SyncWorker", "Foto descargada para arete ${animal.numeroArete}: ${localTarget.name}")
                                    }
                                } catch (e: Exception) {
                                    Log.w("SyncWorker", "No se pudo descargar la foto para ${animal.numeroArete}: ${e.message}")
                                }
                            }
                        }

                        val animalFinal = animal.copy(
                            fotografiaLocal = localFotoPath,
                            fotografiaLocalDisponible = fotoDisponible
                        )
                        database.animalDao().insert(animalFinal)
                    }
                }
            }

            // 4. Egresos y Nómina
            val respEgresos = api.getEgresos()
            if (respEgresos.isSuccessful) {
                respEgresos.body()?.forEach { egreso ->
                    if (egreso.idEgreso !in pendingIds) {
                        database.egresoDao().insert(egreso)
                    }
                }
            }

            val respNomina = api.getNomina()
            if (respNomina.isSuccessful) {
                respNomina.body()?.forEach { nomina ->
                    if (nomina.idNomina !in pendingIds) {
                        database.egresoDao().insertNomina(nomina)
                    }
                }
            }

            // 5. Ventas y Detalle
            val respVentas = api.getVentas()
            if (respVentas.isSuccessful) {
                respVentas.body()?.forEach { venta ->
                    if (venta.idVenta !in pendingIds) {
                        database.ventaDao().insertVenta(venta)
                    }
                }
            }

            val respDetalles = api.getDetallesVenta()
            if (respDetalles.isSuccessful) {
                respDetalles.body()?.forEach { detalle ->
                    if (detalle.idDetalleVenta !in pendingIds) {
                        database.ventaDao().insertDetalle(detalle)
                    }
                }
            }

            // 6. Mortalidad
            val respMortalidad = api.getMortalidad()
            if (respMortalidad.isSuccessful) {
                respMortalidad.body()?.forEach { mortalidad ->
                    if (mortalidad.idMortalidad !in pendingIds) {
                        database.mortalidadDao().insert(mortalidad)
                    }
                }
            }
            Log.d("SyncWorker", "Fase de descarga (PULL) completada exitosamente.")
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error en la fase de descarga (PULL): ${e.message}", e)
        }

        // =====================================================================
        // FASE 2: SUBIDA (PUSH) - De la cola local hacia Supabase
        // =====================================================================
        val pendientes = syncDao.getPendientes()
        if (pendientes.isEmpty()) {
            Log.d("SyncWorker", "No hay datos pendientes para subir.")
            return@withContext Result.success()
        }

        suspend fun asegurarUsuarioEnCloud(userId: String?) {
            if (userId.isNullOrBlank()) return
            try {
                val user = database.usuarioDao().getById(userId)
                if (user != null) {
                    api.insertUsuario(user)
                }
            } catch (e: Exception) {
                Log.w("SyncWorker", "No se pudo asegurar el usuario $userId en la nube", e)
            }
        }

        var algunError = false

        for (item in pendientes) {
            try {
                syncDao.update(item.copy(estado = EstadoSincronizacion.ENVIANDO))
                val eqParam = "eq.${item.idEntidad}"

                val response = when (item.entidad) {
                    "ganado" -> {
                        val dao = database.animalDao()
                        val data = dao.getByArete(item.idEntidad)
                        when (item.operacion) {
                            "INSERT", "UPDATE" -> {
                                if (data != null) {
                                    asegurarUsuarioEnCloud(data.idUsuarioRegistro)

                                    // Subir foto a Supabase Storage si existe archivo local y no se ha subido
                                    var dataToSync = data
                                    if (!data.fotografiaLocal.isNullOrBlank()) {
                                        val localFile = File(data.fotografiaLocal)
                                        if (localFile.exists() && localFile.length() > 0) {
                                            val cloudKey = "${data.numeroArete}.webp"
                                            try {
                                                val reqBody = localFile.asRequestBody("image/webp".toMediaTypeOrNull())
                                                val uploadResp = storageApi.uploadObject(
                                                    bucket = SupabaseClient.BUCKET_ANIMALES,
                                                    path = cloudKey,
                                                    file = reqBody
                                                )
                                                if (uploadResp.isSuccessful) {
                                                    Log.d("SyncWorker", "Fotografía subida a Storage con clave: $cloudKey")
                                                    dataToSync = data.copy(
                                                        fotografiaCloudKey = cloudKey,
                                                        fotografiaLocalDisponible = true
                                                    )
                                                    dao.update(dataToSync)
                                                } else {
                                                    Log.w("SyncWorker", "Fallo subida de foto a Storage: ${uploadResp.code()}")
                                                }
                                            } catch (e: Exception) {
                                                Log.e("SyncWorker", "Error subiendo foto al Storage: ${e.message}", e)
                                            }
                                        }
                                    }

                                    if (item.operacion == "INSERT") {
                                        api.insertAnimal(dataToSync)
                                    } else {
                                        api.updateAnimal(eqParam, dataToSync)
                                    }
                                } else null
                            }
                            "DELETE" -> {
                                // Borrar también la foto del Storage
                                try {
                                    val cloudKey = "${item.idEntidad}.webp"
                                    storageApi.deleteObject(
                                        bucket = SupabaseClient.BUCKET_ANIMALES,
                                        path = cloudKey
                                    )
                                } catch (_: Exception) {}
                                api.deleteAnimal(eqParam)
                            }
                            else -> null
                        }
                    }
                    "ventas" -> {
                        val dao = database.ventaDao()
                        val data = dao.getVentaById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuarioRegistro)
                                    asegurarUsuarioEnCloud(it.idUsuarioAprobacion)
                                    api.insertVenta(it)
                                }
                            }
                            "UPDATE" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuarioRegistro)
                                    asegurarUsuarioEnCloud(it.idUsuarioAprobacion)
                                    api.updateVenta(eqParam, it)
                                }
                            }
                            "DELETE" -> api.deleteVenta(eqParam)
                            else -> null
                        }
                    }
                    "detalle_venta" -> {
                        val dao = database.ventaDao()
                        val data = dao.getDetalleVentaPorArete(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> data?.let { api.insertDetalleVenta(it) }
                            "UPDATE" -> data?.let { api.updateDetalleVenta("eq.${it.idDetalleVenta}", it) }
                            "DELETE" -> api.deleteDetalleVenta(eqParam)
                            else -> null
                        }
                    }
                    "egresos" -> {
                        val dao = database.egresoDao()
                        val data = dao.getEgresoById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuario)
                                    api.insertEgreso(it)
                                }
                            }
                            "UPDATE" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuario)
                                    api.updateEgreso(eqParam, it)
                                }
                            }
                            "DELETE" -> api.deleteEgreso(eqParam)
                            else -> null
                        }
                    }
                    "nomina" -> {
                        null
                    }
                    "mortalidad" -> {
                        val dao = database.mortalidadDao()
                        val data = dao.getMortalidadById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuarioRegistro)
                                    asegurarUsuarioEnCloud(it.idUsuarioAprobacion)
                                    api.insertMortalidad(it)
                                }
                            }
                            "UPDATE" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuarioRegistro)
                                    asegurarUsuarioEnCloud(it.idUsuarioAprobacion)
                                    api.updateMortalidad(eqParam, it)
                                }
                            }
                            "DELETE" -> api.deleteMortalidad(eqParam)
                            else -> null
                        }
                    }
                    "usuarios" -> {
                        val dao = database.usuarioDao()
                        val data = dao.getById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> data?.let { api.insertUsuario(it) }
                            "UPDATE" -> data?.let { api.updateUsuario(eqParam, it) }
                            "DELETE" -> api.deleteUsuario(eqParam)
                            else -> null
                        }
                    }
                    "rangos_peso" -> {
                        null
                    }
                    else -> null
                }

                if (response != null && response.isSuccessful) {
                    Log.d("SyncWorker", "ÉXITO al enviar ${item.entidad} (ID: ${item.idEntidad})")
                    syncDao.update(
                        item.copy(
                            estado = EstadoSincronizacion.SINCRONIZADO,
                            fechaSincronizacion = System.currentTimeMillis()
                        )
                    )
                } else if (response != null) {
                    algunError = true
                    val errorMsg = response.errorBody()?.string() ?: "Error HTTP ${response.code()}"
                    Log.e("SyncWorker", "SUPABASE RECHAZÓ EL DATO. Entidad: ${item.entidad}. Error: $errorMsg")
                    syncDao.update(
                        item.copy(
                            estado = EstadoSincronizacion.ERROR,
                            ultimoError = errorMsg,
                            numeroIntentos = item.numeroIntentos + 1
                        )
                    )
                } else {
                    syncDao.update(
                        item.copy(
                            estado = EstadoSincronizacion.SINCRONIZADO,
                            fechaSincronizacion = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SyncWorker", "Error al sincronizar id: ${item.idEntidad}", e)
                algunError = true
                syncDao.update(
                    item.copy(
                        estado = EstadoSincronizacion.ERROR,
                        ultimoError = e.message ?: "Excepción desconocida",
                        numeroIntentos = item.numeroIntentos + 1
                    )
                )
            }
        }

        // --- PURGA DE REGISTROS ANTIGUOS EN LA COLA ---
        try {
            val limiteDias = 30L
            val tiempoLimiteMs = System.currentTimeMillis() - (limiteDias * 24 * 60 * 60 * 1000)
            syncDao.limpiarCola(tiempoLimiteMs)
            Log.d("SyncWorker", "Purga de historial de sincronización completada.")
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error al purgar historial de sincronización", e)
        }

        if (algunError) {
            return@withContext Result.retry()
        }

        Log.d("SyncWorker", "Sincronización completada exitosamente.")
        return@withContext Result.success()
    }
}
