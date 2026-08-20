package com.rancho.gestion.ui.ganado

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.ConfirmDialog
import com.rancho.gestion.ui.components.SectionCard
import com.rancho.gestion.ui.theme.*
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.ImageOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleAnimalScreen(
    container: AppContainer,
    arete: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val animalState = produceState<Animal?>(initialValue = null, arete) {
        value = container.ganadoRepository.buscarPorArete(arete)
    }

    val animal = animalState.value

    if (animal == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val isArchived = animal.estadoInventario != EstadoInventario.ACTIVO
        
        // Cargar datos extra para archivados
        val ventaExtraState = produceState<Pair<Venta, DetalleVenta>?>(initialValue = null, animal) {
            if (animal.estadoInventario == EstadoInventario.VENDIDO) {
                value = container.ventaRepository.getDetalleVenta(animal.numeroArete)
            }
        }
        val muerteExtraState = produceState<Mortalidad?>(initialValue = null, animal) {
            if (animal.estadoInventario == EstadoInventario.MUERTO) {
                value = container.ganadoRepository.getMortalidadAprobada(animal.numeroArete)
            }
        }

        val ventaExtra = ventaExtraState.value
        val muerteExtra = muerteExtraState.value

        var fotoUriStr by remember { mutableStateOf(animal.fotografiaLocal) }
        var sexo by remember { mutableStateOf(animal.sexo) }
        
        // Formateador de millares
        val formatter = remember { java.text.DecimalFormat("#,###.##") }
        
        var peso by remember { mutableStateOf(formatter.format(animal.pesoEntrada)) }
        var precio by remember { mutableStateOf(formatter.format(animal.precioCompra)) }
        var proveedor by remember { mutableStateOf(animal.proveedor) }
        var fase by remember { mutableStateOf(animal.fase) }
        var color by remember { mutableStateOf(animal.color) }

        // Campos extra para VENTA
        var pesoVenta by remember { mutableStateOf("") }
        var precioVenta by remember { mutableStateOf("") }
        var comprador by remember { mutableStateOf("") }
        
        // Campos extra para MUERTE
        var mermaEconomica by remember { mutableStateOf("") }

        // Inicializar campos extra cuando carguen los datos
        LaunchedEffect(ventaExtra) {
            ventaExtra?.let { (v, d) ->
                pesoVenta = d.pesoSalida.toString()
                precioVenta = d.subtotal.toString()
                comprador = v.comprador
            }
        }
        LaunchedEffect(muerteExtra) {
            muerteExtra?.let { m ->
                mermaEconomica = m.mermaEconomica.toString()
            }
        }

        var confirmacionGuardar by remember { mutableStateOf(false) }
        var confirmacionEliminar by remember { mutableStateOf(false) }
        var mensajeSinCambios by remember { mutableStateOf(false) }
        var errorValidacion by remember { mutableStateOf<String?>(null) }
        var fotoFullScreen by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { BackTopBar(title = "Detalle de Animal", onBack = onBack) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Fotografía (No editable en esta pantalla según nueva regla)
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { if (fotoUriStr != null) fotoFullScreen = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (fotoUriStr != null) {
                        AsyncImage(
                            model = fotoUriStr,
                            contentDescription = "Foto del animal",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Fullscreen, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp))
                        }
                    } else {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_vaca_placeholder),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = RanchoTextSecondary.copy(alpha = 0.2f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                SectionCard {
                    Text("Información General", fontWeight = FontWeight.Bold, color = RanchoPrimary)
                    Spacer(Modifier.height(16.dp))

                    DetailItem("Número de Arete", animal.numeroArete)
                    DetailItem("Estado", if (isArchived) "ARCHIVADO (${animal.estadoInventario.name})" else animal.estadoInventario.name)
                    
                    if (isArchived) {
                        val motivo = when {
                            animal.estadoInventario == EstadoInventario.MUERTO -> "Muerte"
                            animal.tipoBaja == TipoBaja.VENTA -> {
                                if (ventaExtra?.first?.modalidad == ModalidadVenta.JAULA) "Venta por Jaula" else "Venta Individual"
                            }
                            else -> "Baja de sistema"
                        }
                        DetailItem("Motivo de Baja", motivo)
                        DetailItem("Fecha de Baja", DateUtils.formatDay(animal.fechaBaja ?: 0L))
                    }

                    DetailItem("Fecha de Entrada", DateUtils.formatDay(animal.fechaEntrada))
                    
                    val dias = ((System.currentTimeMillis() - animal.fechaEntrada) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                    DetailItem("Días de Estancia", "$dias días")
                }

                Spacer(Modifier.height(16.dp))

                SectionCard {
                    Text(if (isArchived) "Datos de Origen" else "Datos Editables", fontWeight = FontWeight.Bold, color = RanchoPrimary)
                    Spacer(Modifier.height(16.dp))

                    if (!isArchived) {
                        // Sexo (Editable - Dropdown)
                        var sexoMenuAbierto by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = sexoMenuAbierto, onExpandedChange = { sexoMenuAbierto = it }) {
                            OutlinedTextField(
                                value = if (sexo == Sexo.MACHO) "Macho" else "Hembra",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sexo") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sexoMenuAbierto) }
                            )
                            ExposedDropdownMenu(expanded = sexoMenuAbierto, onDismissRequest = { sexoMenuAbierto = false }) {
                                DropdownMenuItem(text = { Text("Macho") }, onClick = { sexo = Sexo.MACHO; sexoMenuAbierto = false })
                                DropdownMenuItem(text = { Text("Hembra") }, onClick = { sexo = Sexo.HEMBRA; sexoMenuAbierto = false })
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Color (Editable - Dropdown)
                        var colorMenuAbierto by remember { mutableStateOf(false) }
                        val opcionesColor = listOf("Negro", "Hosco", "Ballo", "Blanco", "Careto", "Sardo")
                        ExposedDropdownMenuBox(expanded = colorMenuAbierto, onExpandedChange = { colorMenuAbierto = it }) {
                            OutlinedTextField(
                                value = color,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Color") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorMenuAbierto) }
                            )
                            ExposedDropdownMenu(expanded = colorMenuAbierto, onDismissRequest = { colorMenuAbierto = false }) {
                                opcionesColor.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = { color = opt; colorMenuAbierto = false })
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = peso,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                if (filtered.count { it == '.' } <= 1) {
                                    val parts = filtered.split('.')
                                    if ((parts.size < 2 || parts[1].length <= 2) && parts[0].length <= 4) {
                                        peso = filtered
                                        errorValidacion = null
                                    }
                                }
                            },
                            label = { Text("Peso (kg)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = precio,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                if (filtered.count { it == '.' } <= 1) {
                                    val parts = filtered.split('.')
                                    if ((parts.size < 2 || parts[1].length <= 2) && parts[0].length <= 6) {
                                        precio = filtered
                                        errorValidacion = null
                                    }
                                }
                            },
                            label = { Text("Precio de Compra ($)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = proveedor,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isLetter() || it.isWhitespace() || it == 'ñ' || it == 'Ñ' || "áéíóúÁÉÍÓÚ".contains(it) }
                                if (filtered.length <= 30) {
                                    proveedor = filtered
                                    errorValidacion = null
                                }
                            },
                            label = { Text("Proveedor") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        // Fase (Editable - Dropdown)
                        var faseMenuAbierto by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = faseMenuAbierto, onExpandedChange = { faseMenuAbierto = it }) {
                            OutlinedTextField(
                                value = if (fase == FaseGanado.ACOPIO) "Acopio" else "Desarrollo",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Fase") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = faseMenuAbierto) }
                            )
                            ExposedDropdownMenu(expanded = faseMenuAbierto, onDismissRequest = { faseMenuAbierto = false }) {
                                DropdownMenuItem(text = { Text("Acopio") }, onClick = { fase = FaseGanado.ACOPIO; faseMenuAbierto = false })
                                DropdownMenuItem(text = { Text("Desarrollo") }, onClick = { fase = FaseGanado.DESARROLLO; faseMenuAbierto = false })
                            }
                        }
                    } else {
                        // VISTA ARCHIVADA (NO EDITABLE mayoría de campos)
                        DetailItem("Sexo", animal.sexo.name)
                        DetailItem("Color", animal.color)
                        DetailItem("Peso de Compra", "${animal.pesoEntrada.toInt()} kg")
                        DetailItem("Precio de Compra", formatter.format(animal.precioCompra))
                        DetailItem("Proveedor", animal.proveedor)

                        if (animal.estadoInventario == EstadoInventario.VENDIDO) {
                            Spacer(Modifier.height(16.dp))
                            Text("Datos de Venta", fontWeight = FontWeight.Bold, color = RanchoPrimary)
                            Spacer(Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = pesoVenta,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() || it == '.' }
                                    if (filtered.count { it == '.' } <= 1) {
                                        val parts = filtered.split('.')
                                        if ((parts.size < 2 || parts[1].length <= 2) && parts[0].length <= 4) {
                                            pesoVenta = filtered
                                            errorValidacion = null
                                        }
                                    }
                                },
                                label = { Text("Peso de Venta (kg)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = precioVenta,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() || it == '.' }
                                    if (filtered.count { it == '.' } <= 1) {
                                        val parts = filtered.split('.')
                                        if ((parts.size < 2 || parts[1].length <= 2) && parts[0].length <= 6) {
                                            precioVenta = filtered
                                            errorValidacion = null
                                        }
                                    }
                                },
                                label = { Text("Precio de Venta ($)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = comprador,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isLetter() || it.isWhitespace() || it == 'ñ' || it == 'Ñ' || "áéíóúÁÉÍÓÚ".contains(it) }
                                    if (filtered.length <= 30) {
                                        comprador = filtered
                                        errorValidacion = null
                                    }
                                },
                                label = { Text("Comprador") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (animal.estadoInventario == EstadoInventario.MUERTO) {
                            Spacer(Modifier.height(16.dp))
                            Text("Información de Muerte", fontWeight = FontWeight.Bold, color = RanchoError)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = mermaEconomica,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() || it == '.' }
                                    if (filtered.count { it == '.' } <= 1) {
                                        val parts = filtered.split('.')
                                        if ((parts.size < 2 || parts[1].length <= 2) && parts[0].length <= 6) {
                                            val num = filtered.toDoubleOrNull() ?: 0.0
                                            if (num <= 100000) {
                                                mermaEconomica = filtered
                                                errorValidacion = null
                                            }
                                        }
                                    }
                                },
                                label = { Text("Merma Económica ($)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Botones de acción
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { confirmacionEliminar = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar")
                    }
                    Button(
                        onClick = {
                            if (!isArchived) {
                                val pesoD = peso.replace(",", "").toDoubleOrNull()
                                val precioD = precio.replace(",", "").toDoubleOrNull()
                                
                                when {
                                    pesoD == null || pesoD < 40 || pesoD > 1000 -> {
                                        errorValidacion = "El peso debe estar entre 40kg y 1,000kg"
                                    }
                                    precioD == null || precioD < 500 || precioD > 100000 -> {
                                        errorValidacion = "El precio de compra debe estar entre $500 y $100,000"
                                    }
                                    proveedor.isBlank() -> {
                                        errorValidacion = "El proveedor es obligatorio"
                                    }
                                    else -> {
                                        val hayCambios = sexo != animal.sexo || pesoD != animal.pesoEntrada || precioD != animal.precioCompra || proveedor != animal.proveedor || fase != animal.fase || color != animal.color
                                        if (hayCambios) { errorValidacion = null; confirmacionGuardar = true } 
                                        else mensajeSinCambios = true
                                    }
                                }
                            } else {
                                if (animal.estadoInventario == EstadoInventario.VENDIDO) {
                                    val pV = pesoVenta.replace(",", "").toDoubleOrNull()
                                    val prV = precioVenta.replace(",", "").toDoubleOrNull()
                                    if (pV == null || pV <= 0 || pV > 1000) {
                                        errorValidacion = "Ingresa un peso de venta válido (max 1,000)"
                                    } else if (prV == null || prV < 0 || prV > 100000) {
                                        errorValidacion = "Ingresa un precio de venta válido (max 100,000)"
                                    } else if (comprador.isBlank()) {
                                        errorValidacion = "El comprador es obligatorio"
                                    } else {
                                        val (oldV, oldD) = ventaExtra!!
                                        val hayCambios = pV != oldD.pesoSalida || prV != oldD.subtotal || comprador != oldV.comprador
                                        if (hayCambios) confirmacionGuardar = true else mensajeSinCambios = true
                                    }
                                } else if (animal.estadoInventario == EstadoInventario.MUERTO) {
                                    val merma = mermaEconomica.replace(",", "").toDoubleOrNull()
                                    if (merma == null || merma < 0 || merma > 100000) {
                                        errorValidacion = "Ingresa una merma válida (0 a 100,000)"
                                    } else {
                                        val hayCambios = merma != muerteExtra?.mermaEconomica
                                        if (hayCambios) confirmacionGuardar = true else mensajeSinCambios = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = RanchoPrimary)
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar")
                    }
                }
                Spacer(Modifier.height(8.dp))
                errorValidacion?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(40.dp))
            }
        }

        if (mensajeSinCambios) {
            AlertDialog(
                onDismissRequest = { mensajeSinCambios = false },
                confirmButton = { TextButton(onClick = { mensajeSinCambios = false }) { Text("Aceptar") } },
                title = { Text("Sin cambios") },
                text = { Text("No hay cambios por guardar.") }
            )
        }

        if (confirmacionGuardar) {
            val cambios = buildString {
                if (!isArchived) {
                    val nPeso = peso.replace(",", "").toDoubleOrNull() ?: 0.0
                    val nPrecio = precio.replace(",", "").toDoubleOrNull() ?: 0.0
                    if (sexo != animal.sexo) append("Sexo\nValor Anterior: ${animal.sexo}\nValor Nuevo: $sexo\n\n")
                    if (color != animal.color) append("Color\nValor Anterior: ${animal.color}\nValor Nuevo: $color\n\n")
                    if (nPeso != animal.pesoEntrada) append("Peso\nValor Anterior: ${formatter.format(animal.pesoEntrada)} kg\nValor Nuevo: $peso kg\n\n")
                    if (nPrecio != animal.precioCompra) append("Precio de Compra\nValor Anterior: $${formatter.format(animal.precioCompra)}\nValor Nuevo: $$precio\n\n")
                    if (proveedor != animal.proveedor) append("Proveedor\nValor Anterior: ${animal.proveedor}\nValor Nuevo: $proveedor\n\n")
                    if (fase != animal.fase) append("Fase\nValor Anterior: ${animal.fase}\nValor Nuevo: $fase\n\n")
                } else {
                    if (animal.estadoInventario == EstadoInventario.VENDIDO) {
                        val pV = pesoVenta.replace(",", "").toDoubleOrNull() ?: 0.0
                        val prV = precioVenta.replace(",", "").toDoubleOrNull() ?: 0.0
                        val (oldV, oldD) = ventaExtra!!
                        if (pV != oldD.pesoSalida) append("Peso de Venta\nValor Anterior: ${formatter.format(oldD.pesoSalida)} kg\nValor Nuevo: $pesoVenta kg\n\n")
                        if (prV != oldD.subtotal) append("Precio de Venta\nValor Anterior: $${formatter.format(oldD.subtotal)}\nValor Nuevo: $$precioVenta\n\n")
                        if (comprador != oldV.comprador) append("Comprador\nValor Anterior: ${oldV.comprador}\nValor Nuevo: $comprador\n\n")
                    } else if (animal.estadoInventario == EstadoInventario.MUERTO) {
                        val merma = mermaEconomica.replace(",", "").toDoubleOrNull() ?: 0.0
                        if (merma != muerteExtra?.mermaEconomica) append("Merma Económica\nValor Anterior: $${formatter.format(muerteExtra?.mermaEconomica ?: 0.0)}\nValor Nuevo: $$mermaEconomica\n\n")
                    }
                }
            }

            ConfirmDialog(
                title = "Confirmar Cambios",
                message = "Esta seguro que que desea guardar estos cambios para el numero de arete ${animal.numeroArete}\n\n$cambios",
                confirmLabel = "Guardar",
                onConfirm = {
                    scope.launch {
                        if (!isArchived) {
                            val nPeso = peso.replace(",", "").toDoubleOrNull() ?: animal.pesoEntrada
                            val nPrecio = precio.replace(",", "").toDoubleOrNull() ?: animal.precioCompra
                            container.ganadoRepository.editarAnimal(
                                animal.copy(sexo = sexo, color = color, pesoEntrada = nPeso, precioCompra = nPrecio, proveedor = proveedor, fase = fase),
                                esAdmin = true
                            )
                        } else {
                            if (animal.estadoInventario == EstadoInventario.VENDIDO) {
                                val (v, d) = ventaExtra!!
                                val pV = pesoVenta.replace(",", "").toDoubleOrNull() ?: d.pesoSalida
                                val prV = precioVenta.replace(",", "").toDoubleOrNull() ?: d.subtotal
                                container.ventaRepository.actualizarVentaArchivada(
                                    v.copy(comprador = comprador, montoTotal = (v.montoTotal - d.subtotal + prV), pesoTotal = (v.pesoTotal - d.pesoSalida + pV)),
                                    d.copy(pesoSalida = pV, subtotal = prV, precioUnitario = if(pV>0) prV/pV else 0.0)
                                )
                            } else if (animal.estadoInventario == EstadoInventario.MUERTO) {
                                val merma = mermaEconomica.replace(",", "").toDoubleOrNull() ?: muerteExtra!!.mermaEconomica
                                container.ganadoRepository.actualizarMortalidad(muerteExtra!!.copy(mermaEconomica = merma))
                            }
                        }
                        confirmacionGuardar = false
                        onBack()
                    }
                },
                onDismiss = { confirmacionGuardar = false }
            )
        }

        if (confirmacionEliminar) {
            AlertDialog(
                onDismissRequest = { confirmacionEliminar = false },
                icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
                title = { Text("¡Atención!", color = MaterialTheme.colorScheme.error) },
                text = { 
                    Text("¿Está seguro de que desea eliminar por completo el registro del arete número ${animal.numeroArete}? \n\nSi continua toda la información del animal será eliminada por completo y será como si el animal nunca hubiera existido en el sistema.") 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                container.ganadoRepository.eliminarAnimal(animal)
                                confirmacionEliminar = false
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Eliminar por completo") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmacionEliminar = false }) { Text("Cancelar") }
                }
            )
        }

        if (fotoFullScreen && fotoUriStr != null) {
            FullScreenImageDialog(uriStr = fotoUriStr!!, onDismiss = { fotoFullScreen = false })
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = RanchoTextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenImageDialog(uriStr: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                val success = guardarEnGaleria(context, uriStr)
                                withContext(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar(if (success) "Foto guardada en la galería" else "Error al guardar foto")
                                }
                            }
                        }) { Icon(Icons.Default.Download, "Descargar", tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                AsyncImage(model = uriStr, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            }
        }
    }
}

private suspend fun guardarEnGaleria(context: Context, uriStr: String): Boolean {
    return try {
        val file = File(Uri.parse(uriStr).path ?: return false)
        if (!file.exists()) return false
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Rancho_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/FotosGanado")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            true
        } else false
    } catch (e: Exception) {
        false
    }
}
