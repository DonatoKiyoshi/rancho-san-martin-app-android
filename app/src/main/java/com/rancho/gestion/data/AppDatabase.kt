package com.rancho.gestion.data

import android.content.ContentValues
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.rancho.gestion.data.dao.*
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.util.PasswordHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Usuario::class,
        Animal::class,
        RangoPeso::class,
        Venta::class,
        DetalleVenta::class,
        Egreso::class,
        NominaDetalle::class,
        Mortalidad::class,
        ImagenAnimal::class,
        NotificacionEstancia::class,
        ColaSincronizacion::class,
        Dispositivo::class,
        Auditoria::class,
        Configuracion::class
    ],
    version = 9, // Incrementado para añadir tipo_arete
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao
    abstract fun animalDao(): AnimalDao
    abstract fun rangoPesoDao(): RangoPesoDao
    abstract fun ventaDao(): VentaDao
    abstract fun egresoDao(): EgresoDao
    abstract fun mortalidadDao(): MortalidadDao
    abstract fun configuracionDao(): ConfiguracionDao
    abstract fun sincronizacionDao(): SincronizacionDao
    abstract fun auditoriaDao(): AuditoriaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val nuevaPass = "RanchoSanMartin_a5rCNc!Q"
                val hash = PasswordHasher.hash(nuevaPass)
                
                // 1. Asegurar Admin y su contraseña
                db.execSQL("UPDATE usuarios SET password_hash = '$hash' WHERE nombre_usuario = 'admin'")
                
                // 2. Eliminar empleado si existiera
                db.execSQL("DELETE FROM usuarios WHERE nombre_usuario = 'empleado'")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Añadir la columna tipo_arete con valor por defecto SADER
                db.execSQL("ALTER TABLE ganado ADD COLUMN tipo_arete TEXT NOT NULL DEFAULT 'SADER'")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rancho_final_v9.db"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .addCallback(SeedCallback())
                .build().also { INSTANCE = it }
            }
    }

    private class SeedCallback : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.execSQL("PRAGMA foreign_keys = ON;")
            val ahora = System.currentTimeMillis()
            
            // 1. Seed Usuarios (Solo Admin con la nueva contraseña)
            val adminPass = PasswordHasher.hash("RanchoSanMartin_a5rCNc!Q")
            
            val adminValues = ContentValues().apply {
                put("nombre", "Administrador")
                put("nombre_usuario", "admin")
                put("password_hash", adminPass)
                put("rol", "ADMINISTRADOR")
                put("estado", 1)
                put("fecha_creacion", ahora)
                put("fecha_actualizacion", ahora)
                put("version", 1)
            }
            val adminId = db.insert("usuarios", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, adminValues)

            // 2. Seed Rangos de Peso
            val r1 = ContentValues().apply {
                put("limite_inferior", 130.0); put("limite_superior", 220.0); put("nombre_rango", "Becerro")
                put("estado", 1); put("fecha_creacion", ahora); put("fecha_actualizacion", ahora); put("version", 1)
            }
            db.insert("rangos_peso", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, r1)

            val r2 = ContentValues().apply {
                put("limite_inferior", 221.0); put("limite_superior", 280.0); put("nombre_rango", "Añojo")
                put("estado", 1); put("fecha_creacion", ahora); put("fecha_actualizacion", ahora); put("version", 1)
            }
            db.insert("rangos_peso", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, r2)

            // 3. Seed Configuracion
            val c1 = ContentValues().apply {
                put("clave", "MAX_CABEZAS_JAULA"); put("valor", "110"); put("tipo_dato", "INTEGER")
                put("descripcion", "Tope de cabezas por jaula"); put("fecha_actualizacion", ahora)
                put("id_usuario_actualizacion", adminId)
            }
            db.insert("configuracion", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, c1)

            val c2 = ContentValues().apply {
                put("clave", "MAX_PESO_JAULA"); put("valor", "24500"); put("tipo_dato", "DECIMAL")
                put("descripcion", "Tope de kilos por jaula (incluye tolerancia)"); put("fecha_actualizacion", ahora)
                put("id_usuario_actualizacion", adminId)
            }
            db.insert("configuracion", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, c2)
        }
    }
}
