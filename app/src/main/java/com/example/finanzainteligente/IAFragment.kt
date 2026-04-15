package com.wccslic.finanzainteligente

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class IAFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvChat: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    
    private var userMovements = mutableListOf<Movement>()
    private var activeDeudas = mutableListOf<Map<String, Any>>()
    private var totalBalance = 0.0
    private var serviciosRecurrentes = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ia, container, false)

        auth = FirebaseAuth.getInstance()
        rvChat = view.findViewById(R.id.rvChat)
        val etChatMessage = view.findViewById<TextInputEditText>(R.id.etChatMessage)
        val btnSendMessage = view.findViewById<MaterialButton>(R.id.btnSendMessage)
        val btnExportReport = view.findViewById<MaterialButton>(R.id.btnExportReport)

        adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(context)
        rvChat.adapter = adapter

        loadUserMovementsAndInitialSummary()

        btnSendMessage.setOnClickListener {
            val userText = etChatMessage.text.toString().trim()
            if (userText.isNotEmpty()) {
                addMessage(userText, "User")
                etChatMessage.text?.clear()
                processAIResponse(userText)
            }
        }

        btnExportReport.setOnClickListener {
            showExportOptions()
        }

        return view
    }

    private fun loadUserMovementsAndInitialSummary() {
        val uid = auth.currentUser?.uid ?: return
        
        db.collection("usuarios_finanzas").document(uid).get().addOnSuccessListener { doc ->
            totalBalance = doc.getDouble("saldo") ?: 0.0
            serviciosRecurrentes = doc.getDouble("servicios") ?: 0.0
        }

        // Load movements
        db.collection("usuarios_finanzas").document(uid)
            .collection("movimientos")
            .get()
            .addOnSuccessListener { snapshots ->
                userMovements.clear()
                for (doc in snapshots) {
                    val movement = doc.toObject(Movement::class.java)
                    userMovements.add(movement)
                }
                
                // Load active debts for recurring payments logic
                db.collection("usuarios_finanzas").document(uid)
                    .collection("deudas")
                    .get()
                    .addOnSuccessListener { debtSnapshots ->
                        activeDeudas.clear()
                        for (doc in debtSnapshots) {
                            activeDeudas.add(doc.data)
                        }
                        showInitialSummary()
                    }
            }
    }

    private fun showInitialSummary() {
        val fifteenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -15) }.timeInMillis
        val recentMovements = userMovements.filter { it.fecha >= fifteenDaysAgo }
        val totalIngresos = recentMovements.filter { it.tipo == "Ingreso" }.sumOf { it.monto }
        val totalGastosBase = recentMovements.filter { it.tipo == "Gasto" && !it.isDeuda }.sumOf { it.monto }
        
        // Calculate monthly debt obligations
        val totalMensualDeudas = activeDeudas.sumOf { (it["pagoMensual"] as? Double) ?: 0.0 }
        val totalDeudaBalance = userMovements.filter { it.isDeuda }.sumOf { it.monto }
        
        val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        
        val summary = StringBuilder()
        summary.append("¡Hola! Soy tu Asistente IA Financiero.\n\n")
        summary.append("Resumen de los últimos 15 días:\n")
        summary.append("• Ingresos: ${format.format(totalIngresos)}\n")
        summary.append("• Gastos Variables: ${format.format(totalGastosBase)}\n")
        summary.append("• Compromisos Mensuales (Deudas/Servicios): ${format.format(totalMensualDeudas + serviciosRecurrentes)}\n")
        summary.append("• Deuda Total Pendiente: ${format.format(totalDeudaBalance)}\n")
        
        val totalGastosFactored = totalGastosBase + totalMensualDeudas + serviciosRecurrentes
        val periodBalance = totalIngresos - totalGastosFactored
        val availableBalance = totalBalance - totalMensualDeudas - serviciosRecurrentes
        
        summary.append("• Balance estimado periodo (15 días): ${format.format(periodBalance)}\n")
        summary.append("• Saldo Real Disponible: ${format.format(availableBalance)}\n")

        summary.append("\n¿En qué te puedo ayudar hoy?")
        addMessage(summary.toString(), "IA")
    }

    private fun addMessage(text: String, sender: String) {
        messages.add(ChatMessage(text, sender))
        adapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    private fun processAIResponse(query: String) {
        val lowerQuery = query.lowercase()
        val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

        val response = when {
            lowerQuery.contains("cuánto gasté") || lowerQuery.contains("gastos") -> {
                val totalGastosBase = userMovements.filter { it.tipo == "Gasto" && !it.isDeuda }.sumOf { it.monto }
                val totalMensualDeudas = activeDeudas.sumOf { (it["pagoMensual"] as? Double) ?: 0.0 }
                val totalCompromisos = totalMensualDeudas + serviciosRecurrentes
                "Has gastado ${format.format(totalGastosBase)} en variables, más ${format.format(totalCompromisos)} de compromisos mensuales fijos (deudas y servicios)."
            }
            lowerQuery.contains("deuda") || lowerQuery.contains("debo") -> {
                val totalDeuda = userMovements.filter { it.isDeuda }.sumOf { it.monto }
                "Tu deuda total pendiente es de ${format.format(totalDeuda)}. Tienes ${activeDeudas.size} deudas activas con pagos mensuales."
            }
            lowerQuery.contains("balance") || lowerQuery.contains("resumen") || lowerQuery.contains("saldo") -> {
                val totalMensualDeudas = activeDeudas.sumOf { (it["pagoMensual"] as? Double) ?: 0.0 }
                val availableBalance = totalBalance - totalMensualDeudas - serviciosRecurrentes
                "Tu saldo total disponible (restando compromisos) es de ${format.format(availableBalance)}."
            }
            else -> "Puedo darte detalles sobre tus gastos, deudas o el balance total. ¿Deseas exportar un reporte detallado?"
        }

        rvChat.postDelayed({ addMessage(response, "IA") }, 1000)
    }

    private fun showExportOptions() {
        val options = arrayOf("Reporte PDF Profesional", "Reporte Excel", "Compartir App (Código QR)")
        AlertDialog.Builder(requireContext())
            .setTitle("Exportar o Compartir")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> generatePDF()
                    1 -> generateExcel()
                    2 -> showAppQRCode()
                }
            }
            .show()
    }

    private fun showAppQRCode() {
        val appPackageName = requireContext().packageName
        val playStoreLink = "https://play.google.com/store/apps/details?id=$appPackageName"
        
        try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix = multiFormatWriter.encode(playStoreLink, BarcodeFormat.QR_CODE, 500, 500)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.createBitmap(bitMatrix)
            
            val imageView = ImageView(requireContext())
            imageView.setImageBitmap(bitmap)
            imageView.setPadding(32, 32, 32, 32)
            
            AlertDialog.Builder(requireContext())
                .setTitle("Escanea para descargar")
                .setView(imageView)
                .setPositiveButton("Compartir Imagen") { _, _ ->
                    shareQRCodeImage(bitmap)
                }
                .setNegativeButton("Cerrar", null)
                .show()
                
        } catch (e: WriterException) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareQRCodeImage(bitmap: Bitmap) {
        try {
            val file = File(requireContext().externalCacheDir, "QR_Descarga_Finanza.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir código QR"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vectorToBitmap(drawableId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun generatePDF() {
        try {
            val file = File(requireContext().externalCacheDir, "Reporte_Financiero.pdf")
            val writer = PdfWriter(FileOutputStream(file))
            val pdf = PdfDocument(writer)
            val document = Document(pdf)
            val mxnFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

            // Logo
            val logoBitmap = vectorToBitmap(R.drawable.ic_app_logo)
            if (logoBitmap != null) {
                val stream = ByteArrayOutputStream()
                logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val img = Image(ImageDataFactory.create(stream.toByteArray()))
                img.setWidth(60f)
                img.setHorizontalAlignment(HorizontalAlignment.CENTER)
                document.add(img)
            }

            // Título
            document.add(Paragraph("Gastos Inteligentes")
                .setTextAlignment(TextAlignment.CENTER)
                .setBold().setFontSize(22f))
            
            document.add(Paragraph("Reporte de Movimientos")
                .setTextAlignment(TextAlignment.CENTER)
                .setBold().setFontSize(16f))

            document.add(Paragraph("\n"))

            // Información del Usuario y Fecha
            document.add(Paragraph("Usuario: ${auth.currentUser?.email ?: "N/A"}")
                .setFontSize(11f))
            document.add(Paragraph("Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}")
                .setFontSize(11f))
            
            val totalMensualDeudas = activeDeudas.sumOf { (it["pagoMensual"] as? Double) ?: 0.0 }
            val availableBalance = totalBalance - totalMensualDeudas - serviciosRecurrentes
            
            document.add(Paragraph("SALDO DISPONIBLE REAL: ${mxnFormat.format(availableBalance)}")
                .setBold().setFontSize(14f).setTextAlignment(TextAlignment.LEFT))
            
            document.add(Paragraph("Saldo Bruto: ${mxnFormat.format(totalBalance)}").setFontSize(10f))

            document.add(Paragraph("\n"))

            // Section: Monthly Obligations
            if (activeDeudas.isNotEmpty() || serviciosRecurrentes > 0) {
                document.add(Paragraph("COMPROMISOS MENSUALES").setBold().setUnderline())
                
                if (activeDeudas.isNotEmpty()) {
                    val debtTable = Table(floatArrayOf(3f, 2f, 2f, 2f)).useAllAvailableWidth()
                    debtTable.addCell("Nombre Deuda")
                    debtTable.addCell("Tipo")
                    debtTable.addCell("Tasa Interés")
                    debtTable.addCell("Pago Mensual")

                    for (deuda in activeDeudas) {
                        debtTable.addCell(deuda["nombre"]?.toString() ?: "N/A")
                        debtTable.addCell(deuda["tipo"]?.toString() ?: "N/A")
                        debtTable.addCell("${deuda["tasaInteres"]}%")
                        debtTable.addCell(mxnFormat.format((deuda["pagoMensual"] as? Double) ?: 0.0))
                    }
                    document.add(debtTable)
                }
                
                if (serviciosRecurrentes > 0) {
                    document.add(Paragraph("Servicios Recurrentes: ${mxnFormat.format(serviciosRecurrentes)}"))
                }

                val totalMensual = totalMensualDeudas + serviciosRecurrentes
                document.add(Paragraph("Total Compromisos Mensuales: ${mxnFormat.format(totalMensual)}").setBold())
                document.add(Paragraph("\n"))
            }
            
            document.add(Paragraph("DETALLE DE MOVIMIENTOS HISTÓRICOS").setBold().setUnderline())
            val table = Table(floatArrayOf(2f, 3f, 2f, 2f)).useAllAvailableWidth()
            table.addCell("Fecha")
            table.addCell("Descripción/Categoría")
            table.addCell("Tipo")
            table.addCell("Monto")

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            for (mov in userMovements.sortedByDescending { it.fecha }) {
                table.addCell(sdf.format(Date(mov.fecha)))
                table.addCell("${mov.categoria}\n${mov.descripcion}")
                table.addCell(mov.tipo)
                table.addCell(mxnFormat.format(mov.monto))
            }
            document.add(table)

            document.add(Paragraph("\nGenerado por Asistente IA Gastos Inteligentes").setItalic().setTextAlignment(TextAlignment.CENTER))
            document.close()
            sendEmailWithAttachment(file)
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateExcel() {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Resumen Financiero")
            val mxnFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

            val totalMensualDeudas = activeDeudas.sumOf { (it["pagoMensual"] as? Double) ?: 0.0 }
            val availableBalance = totalBalance - totalMensualDeudas - serviciosRecurrentes

            // Sección de Resumen
            val r1 = sheet.createRow(0); r1.createCell(0).setCellValue("RESUMEN GENERAL")
            val r2 = sheet.createRow(1); r2.createCell(0).setCellValue("Saldo Disponible:"); r2.createCell(1).setCellValue(availableBalance)
            val r3 = sheet.createRow(2); r3.createCell(0).setCellValue("Saldo Bruto:"); r3.createCell(1).setCellValue(totalBalance)
            
            val totalIng = userMovements.filter { it.tipo == "Ingreso" }.sumOf { it.monto }
            val totalGas = userMovements.filter { it.tipo == "Gasto" && !it.isDeuda }.sumOf { it.monto }
            val totalDeu = userMovements.filter { it.isDeuda }.sumOf { it.monto }

            sheet.createRow(3).apply { createCell(0).setCellValue("Total Ingresos:"); createCell(1).setCellValue(totalIng) }
            sheet.createRow(4).apply { createCell(0).setCellValue("Total Gastos Variables:"); createCell(1).setCellValue(totalGas) }
            sheet.createRow(5).apply { createCell(0).setCellValue("Compromisos Mensuales:"); createCell(1).setCellValue(totalMensualDeudas + serviciosRecurrentes) }
            sheet.createRow(6).apply { createCell(0).setCellValue("Total Deudas (Capital):"); createCell(1).setCellValue(totalDeu) }

            // Tabla de Movimientos
            val headerRow = sheet.createRow(9)
            headerRow.createCell(0).setCellValue("Fecha")
            headerRow.createCell(1).setCellValue("Categoría")
            headerRow.createCell(2).setCellValue("Descripción")
            headerRow.createCell(3).setCellValue("Tipo")
            headerRow.createCell(4).setCellValue("Monto")

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            for ((index, mov) in userMovements.sortedByDescending { it.fecha }.withIndex()) {
                val row = sheet.createRow(index + 10)
                row.createCell(0).setCellValue(sdf.format(Date(mov.fecha)))
                row.createCell(1).setCellValue(mov.categoria)
                row.createCell(2).setCellValue(mov.descripcion)
                row.createCell(3).setCellValue(mov.tipo)
                row.createCell(4).setCellValue(mov.monto)
            }

            val file = File(requireContext().externalCacheDir, "Reporte_Financiero.xlsx")
            workbook.write(FileOutputStream(file))
            workbook.close()
            sendEmailWithAttachment(file)
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmailWithAttachment(file: File) {
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_SUBJECT, "Tu Reporte Financiero Personalizado")
            val totalMensual = activeDeudas.sumOf { (it["pagoMensual"] as? Double) ?: 0.0 } + serviciosRecurrentes
            val availableBalance = totalBalance - (totalMensual - serviciosRecurrentes) - serviciosRecurrentes
            putExtra(Intent.EXTRA_TEXT, "Hola,\n\nAdjuntamos tu reporte de gastos generado por la IA de Gastos Inteligentes.\n\nSaldo Disponible: ${NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(availableBalance)}\nCompromisos Mensuales: ${NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(totalMensual)}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", uri)
        }
        startActivity(Intent.createChooser(emailIntent, "Enviar Reporte..."))
    }
}
