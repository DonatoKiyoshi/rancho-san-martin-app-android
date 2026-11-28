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
        Log.d("SyncWorker", "Iniciando sincronización bidireccional con Supabase...")

        val database = AppDatabase.getInstance(applicationContext)
        val syncDao = database.sincronizacionDao()
        val api = SupabaseClient.retrofit.create(SupabaseApi::class.java)
        val storageApi = SupabaseClient.storageRetrofit.create(SupabaseStorageApi::class.java)

        val photosDir = File(applicationContext.filesDir, "animal_photos").apply {
            if (!exists()) mkdirs()
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

        // =====================================================================
        // FASE 1: SUBIDA (PUSH) - De la cola local hacia Supabase primero
        // (Para que la nube refleje los últimos cambios de este celular)
        // =====================================================================
        val pendientes = syncDao.getPendientes()
        var algunErrorPush = false

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
                                                }
                                            } catch (e: Exception) {
                                                Log.e("SyncWorker", "Error subiendo foto al Storage: ${e.message}", e)
                                            }
                                        }
                                    }

                                    // Usamos insertAnimal con Prefer: resolution=merge-duplicates (Upsert)
                                    api.insertAnimal(dataToSync)
                                } else null
                            }
                            "DELETE" -> {
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
                            "INSERT", "UPDATE" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuarioRegistro)
                                    asegurarUsuarioEnCloud(it.idUsuarioAprobacion)
                                    api.insertVenta(it)
                                }
                            }
                            "DELETE" -> api.deleteVenta(eqParam)
                            else -> null
                        }
                    }
                    "detalle_venta" -> {
                        val dao = database.ventaDao()
                        val data = dao.getDetalleById(item.idEntidad) ?: dao.getDetalleVentaPorArete(item.idEntidad)
                        when (item.operacion) {
                            "INSERT", "UPDATE" -> data?.let { api.insertDetalleVenta(it) }
                            "DELETE" -> api.deleteDetalleVenta(eqParam)
                            else -> null
                        }
                    }
                    "egresos" -> {
                        val dao = database.egresoDao()
                        val data = dao.getEgresoById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT", "UPDATE" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuario)
                                    api.insertEgreso(it)
                                }
                            }
                            "DELETE" -> api.deleteEgreso(eqParam)
                            else -> null
                        }
                    }
                    "nomina" -> {
                        val dao = database.egresoDao()
                        val data = dao.getNominaById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT", "UPDATE" -> data?.let { api.insertNomina(it) }
                            "DELETE" -> api.deleteNomina(eqParam)
                            else -> null
                        }
                    }
                    "mortalidad" -> {
                        val dao = database.mortalidadDao()
                        val data = dao.getMortalidadById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT", "UPDATE" -> {
                                data?.let {
                                    asegurarUsuarioEnCloud(it.idUsuarioRegistro)
                                    asegurarUsuarioEnCloud(it.idUsuarioAprobacion)
                                    api.insertMortalidad(it)
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
                            "INSERT", "UPDATE" -> data?.let { api.insertUsuario(it) }
                            "DELETE" -> api.deleteUsuario(eqParam)
                            else -> null
                        }
                    }
                    "rangos_peso" -> {
                        val dao = database.rangoPesoDao()
                        val data = dao.getById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT", "UPDATE" -> data?.let { api.insertRangoPeso(it) }
                            "DELETE" -> api.deleteRangoPeso(eqParam)
                            else -> null
                        }
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
                    algunErrorPush = true
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
                algunErrorPush = true
                syncDao.update(
                    item.copy(
                        estado = EstadoSincronizacion.ERROR,
                        ultimoError = e.message ?: "Excepción desconocida",
                        numeroIntentos = item.numeroIntentos + 1
                    )
                )
            }
        }

        // =====================================================================
        // FASE 2: DESCARGA Y RECONCILIACIÓN (PULL) - De Supabase hacia SQLite
        // (Sincroniza inserciones, actualizaciones y eliminaciones de la nube)
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
                val cloudUsuarios = respUsuarios.body() ?: emptyList()
                cloudUsuarios.forEach { usuario ->
                    if (usuario.idUsuario !in pendingIds) {
                        database.usuarioDao().insert(usuario)
                    }
                }
            }

            // 2. Rangos de Peso (con espejo de eliminaciones)
            val respRangos = api.getRangosPeso()
            if (respRangos.isSuccessful) {
                val cloudRangos = respRangos.body() ?: emptyList()
                val cloudIds = cloudRangos.map { it.idRangoPeso }.toSet()
                cloudRangos.forEach { rango ->
                    if (rango.idRangoPeso !in pendingIds) {
                        database.rangoPesoDao().insert(rango)
                    }
                }
                database.rangoPesoDao().getAllOnce().forEach { local ->
                    if (local.idRangoPeso !in cloudIds && local.idRangoPeso !in pendingIds) {
                        database.rangoPesoDao().delete(local)
                    }
                }
            }

            // 3. Ganado, fotografías y espejo de eliminaciones
            val respGanado = api.getGanado()
            if (respGanado.isSuccessful) {
                val cloudGanado = respGanado.body() ?: emptyList()
                val cloudAretes = cloudGanado.map { it.numeroArete }.toSet()

                cloudGanado.forEach { animal ->
                    if (animal.numeroArete !in pendingIds) {
                        var localFotoPath = animal.fotografiaLocal
                        var fotoDisponible = animal.fotografiaLocalDisponible

                        // Verificar si el path local guardado realmente existe en ESTE teléfono
                        if (!localFotoPath.isNullOrBlank() && !File(localFotoPath).exists()) {
                            localFotoPath = null
                            fotoDisponible = false
                        }

                        val cloudKey = animal.fotografiaCloudKey
                        if (!cloudKey.isNullOrBlank()) {
                            val localTarget = File(photosDir, cloudKey)
                            // Siempre descargamos si no existe o si hubo actualización reciente
                            val localAnimalPrevio = database.animalDao().getByArete(animal.numeroArete)
                            val debeDescargar = !localTarget.exists() ||
                                localTarget.length() == 0L ||
                                (localAnimalPrevio != null && animal.fechaActualizacion > localAnimalPrevio.fechaActualizacion)

                            if (debeDescargar) {
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
                                    }
                                } catch (e: Exception) {
                                    Log.w("SyncWorker", "No se pudo descargar la foto para ${animal.numeroArete}: ${e.message}")
                                }
                            } else {
                                localFotoPath = localTarget.absolutePath
                                fotoDisponible = true
                            }
                        }

                        val animalFinal = animal.copy(
                            fotografiaLocal = localFotoPath,
                            fotografiaLocalDisponible = fotoDisponible
                        )
                        database.animalDao().insert(animalFinal)
                    }
                }

                // Si un animal fue eliminado en otro teléfono o en Supabase, eliminarlo localmente
                database.animalDao().getAllOnce().forEach { local ->
                    if (local.numeroArete !in cloudAretes && local.numeroArete !in pendingIds) {
                        database.animalDao().delete(local)
                    }
                }
            }

            // 4. Egresos y Nómina (con espejo de eliminaciones)
            val respEgresos = api.getEgresos()
            if (respEgresos.isSuccessful) {
                val cloudEgresos = respEgresos.body() ?: emptyList()
                val cloudIds = cloudEgresos.map { it.idEgreso }.toSet()
                cloudEgresos.forEach { egreso ->
                    if (egreso.idEgreso !in pendingIds) {
                        database.egresoDao().insert(egreso)
                    }
                }
                database.egresoDao().getAllOnce().forEach { local ->
                    if (local.idEgreso !in cloudIds && local.idEgreso !in pendingIds) {
                        database.egresoDao().delete(local)
                    }
                }
            }

            val respNomina = api.getNomina()
            if (respNomina.isSuccessful) {
                val cloudNomina = respNomina.body() ?: emptyList()
                val cloudIds = cloudNomina.map { it.idNomina }.toSet()
                cloudNomina.forEach { nomina ->
                    if (nomina.idNomina !in pendingIds) {
                        database.egresoDao().insertNomina(nomina)
                    }
                }
                database.egresoDao().getAllNominaOnce().forEach { local ->
                    if (local.idNomina !in cloudIds && local.idNomina !in pendingIds) {
                        database.egresoDao().deleteNomina(local)
                    }
                }
            }

            // 5. Ventas y Detalle (con espejo de eliminaciones)
            val respVentas = api.getVentas()
            if (respVentas.isSuccessful) {
                val cloudVentas = respVentas.body() ?: emptyList()
                val cloudIds = cloudVentas.map { it.idVenta }.toSet()
                cloudVentas.forEach { venta ->
                    if (venta.idVenta !in pendingIds) {
                        database.ventaDao().insertVenta(venta)
                    }
                }
                database.ventaDao().getAllVentasOnce().forEach { local ->
                    if (local.idVenta !in cloudIds && local.idVenta !in pendingIds) {
                        database.ventaDao().deleteVentaById(local.idVenta)
                    }
                }
            }

            val respDetalles = api.getDetallesVenta()
            if (respDetalles.isSuccessful) {
                val cloudDetalles = respDetalles.body() ?: emptyList()
                val cloudIds = cloudDetalles.map { it.idDetalleVenta }.toSet()
                cloudDetalles.forEach { detalle ->
                    if (detalle.idDetalleVenta !in pendingIds) {
                        database.ventaDao().insertDetalle(detalle)
                    }
                }
                database.ventaDao().getAllDetallesOnce().forEach { local ->
                    if (local.idDetalleVenta !in cloudIds && local.idDetalleVenta !in pendingIds && local.numeroArete !in pendingIds) {
                        database.ventaDao().deleteDetalle(local)
                    }
                }
            }

            // 6. Mortalidad (con espejo de eliminaciones)
            val respMortalidad = api.getMortalidad()
            if (respMortalidad.isSuccessful) {
                val cloudMortalidad = respMortalidad.body() ?: emptyList()
                val cloudIds = cloudMortalidad.map { it.idMortalidad }.toSet()
                cloudMortalidad.forEach { mortalidad ->
                    if (mortalidad.idMortalidad !in pendingIds) {
                        database.mortalidadDao().insert(mortalidad)
                    }
                }
                database.mortalidadDao().getAllOnce().forEach { local ->
                    if (local.idMortalidad !in cloudIds && local.idMortalidad !in pendingIds) {
                        database.mortalidadDao().eliminarSolicitud(local.idMortalidad)
                    }
                }
            }

            Log.d("SyncWorker", "Fase de descarga y reconciliación (PULL) completada exitosamente.")
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error en la fase de descarga (PULL): ${e.message}", e)
        }

        // --- PURGA DE REGISTROS ANTIGUOS EN LA COLA ---
        try {
            val limiteDias = 30L
            val tiempoLimiteMs = System.currentTimeMillis() - (limiteDias * 24 * 60 * 60 * 1000)
            syncDao.limpiarCola(tiempoLimiteMs)
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error al purgar historial de sincronización", e)
        }

        if (algunErrorPush) {
            return@withContext Result.retry()
        }

        Log.d("SyncWorker", "Sincronización bidireccional completada exitosamente.")
        return@withContext Result.success()
    }
}
