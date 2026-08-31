package com.rancho.gestion.data.entities

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromRol(value: Rol): String = value.name
    @TypeConverter fun toRol(value: String): Rol = Rol.valueOf(value)

    @TypeConverter fun fromSexo(value: Sexo): String = value.name
    @TypeConverter fun toSexo(value: String): Sexo = Sexo.valueOf(value)

    @TypeConverter fun fromFaseGanado(value: FaseGanado): String = value.name
    @TypeConverter fun toFaseGanado(value: String): FaseGanado = FaseGanado.valueOf(value)

    @TypeConverter fun fromEstadoInventario(value: EstadoInventario): String = value.name
    @TypeConverter fun toEstadoInventario(value: String): EstadoInventario = EstadoInventario.valueOf(value)

    @TypeConverter fun fromEstadoAprobacion(value: EstadoAprobacion): String = value.name
    @TypeConverter fun toEstadoAprobacion(value: String): EstadoAprobacion = EstadoAprobacion.valueOf(value)

    @TypeConverter fun fromModalidadVenta(value: ModalidadVenta): String = value.name
    @TypeConverter fun toModalidadVenta(value: String): ModalidadVenta = ModalidadVenta.valueOf(value)

    @TypeConverter fun fromCategoriaEgreso(value: CategoriaEgreso): String = value.name
    @TypeConverter fun toCategoriaEgreso(value: String): CategoriaEgreso = CategoriaEgreso.valueOf(value)

    @TypeConverter fun fromTipoBaja(value: TipoBaja): String = value.name
    @TypeConverter fun toTipoBaja(value: String): TipoBaja = TipoBaja.valueOf(value)

    @TypeConverter fun fromEstadoSincronizacion(value: EstadoSincronizacion): String = value.name
    @TypeConverter fun toEstadoSincronizacion(value: String): EstadoSincronizacion = EstadoSincronizacion.valueOf(value)

    @TypeConverter fun fromTipoArete(value: TipoArete): String = value.name
    @TypeConverter fun toTipoArete(value: String): TipoArete {
        return when (value) {
            "GENERICO" -> TipoArete.OTRO
            else -> try { TipoArete.valueOf(value) } catch (e: Exception) { TipoArete.SADER }
        }
    }
}
