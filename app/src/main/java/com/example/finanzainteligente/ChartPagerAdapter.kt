package com.wccslic.finanzainteligente

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChartPagerAdapter : RecyclerView.Adapter<ChartPagerAdapter.ViewHolder>() {

    private var totalIngresos = 0.0
    private var totalGastos = 0.0
    private var totalDeudas = 0.0
    private var history = listOf<Movement>()

    fun setData(ingresos: Double, gastos: Double, deudas: Double, history: List<Movement>) {
        if (this.totalIngresos == ingresos && this.totalGastos == gastos && 
            this.totalDeudas == deudas && this.history.size == history.size) {
            return 
        }
        this.totalIngresos = ingresos
        this.totalGastos = gastos
        this.totalDeudas = deudas
        this.history = history
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pieChart: PieChart = view.findViewById(R.id.pagerPieChart)
        val lineChart: LineChart = view.findViewById(R.id.pagerLineChart)
        val scrollAI: View = view.findViewById(R.id.scrollAIAnalysis)
        val tvDeepAnalysis: TextView = view.findViewById(R.id.tvAIDeepAnalysis)
        val tvSuggestion: TextView = view.findViewById(R.id.tvChartAISuggestion)
        val layoutSuggestion: View = view.findViewById(R.id.layoutAISuggestion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chart_pager, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (position) {
            0 -> {
                holder.pieChart.visibility = View.VISIBLE
                holder.lineChart.visibility = View.GONE
                holder.scrollAI.visibility = View.GONE
                holder.layoutSuggestion.visibility = View.VISIBLE
                holder.tvSuggestion.text = generateShortSuggestion()
                setupPieChart(holder.pieChart)
            }
            1 -> {
                holder.pieChart.visibility = View.GONE
                holder.lineChart.visibility = View.VISIBLE
                holder.scrollAI.visibility = View.GONE
                holder.layoutSuggestion.visibility = View.VISIBLE
                holder.tvSuggestion.text = "Tendencia semanal (Ingresos, Gastos, Deudas)."
                setupLineChart(holder.lineChart)
            }
            2 -> {
                holder.pieChart.visibility = View.GONE
                holder.lineChart.visibility = View.GONE
                holder.scrollAI.visibility = View.VISIBLE
                holder.layoutSuggestion.visibility = View.GONE
                setupDeepAnalysis(holder.tvDeepAnalysis)
            }
        }
    }

    override fun getItemCount(): Int = 3

    private fun generateShortSuggestion(): String {
        val totalSalida = totalGastos
        return when {
            history.isEmpty() -> "Registra movimientos para recibir consejos."
            totalSalida > totalIngresos -> "⚠️ Las salidas superan tus ingresos totales."
            totalSalida > totalIngresos * 0.8 -> "📉 Tus gastos son elevados."
            else -> "✅ Buen control de tu balance general."
        }
    }

    private fun setupDeepAnalysis(tv: TextView) {
        val sb = StringBuilder()
        val mxnFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        
        val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        val recentMovements = history.filter { it.fecha >= sevenDaysAgo }
        
        sb.append("Resumen de los últimos 7 días:\n")
        
        if (recentMovements.isEmpty() && totalIngresos == 0.0 && totalGastos == 0.0 && totalDeudas == 0.0) {
            sb.append("No hay movimientos suficientes esta semana.")
        } else {
            val gastosSemana = recentMovements.filter { it.tipo == "Gasto" && !it.id.startsWith("recurring_") }.sumOf { it.monto }
            val ingresosSemana = recentMovements.filter { it.tipo == "Ingreso" && !it.isSaldoInicial }.sumOf { it.monto }
            val deudasSemana = recentMovements.filter { it.isDeuda }.sumOf { it.monto }
            
            sb.append("• Has gastado ${mxnFormat.format(gastosSemana)} esta semana.\n")
            sb.append("• Has ingresado ${mxnFormat.format(ingresosSemana)} esta semana.\n")
            
            if (deudasSemana > 0) {
                sb.append("• Nueva deuda registrada: ${mxnFormat.format(deudasSemana)}\n")
            }
            sb.append("\n")
            
            val topCategory = recentMovements.filter { it.tipo == "Gasto" && !it.id.startsWith("recurring_") }
                .groupBy { it.categoria }
                .maxByOrNull { entry -> entry.value.sumOf { it.monto } }
            
            if (topCategory != null) {
                sb.append("Análisis IA: Tu mayor gasto semanal fue en '${topCategory.key}'.\n\n")
            }

            sb.append("Consejo IA: ")
            val totalSalida = gastosSemana
            if (totalSalida > ingresosSemana && ingresosSemana > 0) {
                sb.append("Tus salidas superan tus ingresos semanales.")
            } else if (deudasSemana > 0) {
                sb.append("Prioriza el pago de tus deudas para ahorrar en intereses.")
            } else {
                sb.append("¡Excelente manejo! Sigue así para fortalecer tu salud financiera.")
            }
        }
        tv.text = sb.toString()
    }

    private fun setupPieChart(pieChart: PieChart) {
        val mxnFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        val entries = ArrayList<PieEntry>()
        
        val saldoInicialTotal = history.filter { it.isSaldoInicial }.sumOf { it.monto }
        val prestamosTotal = history.filter { it.isDeuda && it.tipo == "Ingreso" }.sumOf { it.monto }
        val ingresosMensualesTotal = totalIngresos - saldoInicialTotal - prestamosTotal
        
        var remainingGasto = totalGastos
        var availableEarnings = ingresosMensualesTotal
        var availableInitial = saldoInicialTotal
        var availableLoans = prestamosTotal
        
        if (remainingGasto > 0) {
            val spentFromEarnings = minOf(availableEarnings, remainingGasto)
            availableEarnings -= spentFromEarnings
            remainingGasto -= spentFromEarnings
        }
        if (remainingGasto > 0) {
            val spentFromInitial = minOf(availableInitial, remainingGasto)
            availableInitial -= spentFromInitial
            remainingGasto -= spentFromInitial
        }
        if (remainingGasto > 0) {
            val spentFromLoans = minOf(availableLoans, remainingGasto)
            availableLoans -= spentFromLoans
            remainingGasto -= spentFromLoans
        }

        if (totalGastos > 0) entries.add(PieEntry(totalGastos.toFloat(), "Gastos"))
        if (availableEarnings > 0) entries.add(PieEntry(availableEarnings.toFloat(), "Ingresos"))
        if (availableInitial > 0) entries.add(PieEntry(availableInitial.toFloat(), "Saldo Inicial"))
        if (availableLoans > 0) entries.add(PieEntry(availableLoans.toFloat(), "Préstamos"))

        if (entries.isEmpty()) {
            pieChart.clear()
            return
        }

        val dataSet = PieDataSet(entries, "")
        val colors = mutableListOf<Int>()
        
        if (totalGastos > 0) colors.add(Color.parseColor("#F44336")) 
        if (availableEarnings > 0) colors.add(Color.parseColor("#4CAF50"))
        if (availableInitial > 0) colors.add(Color.parseColor("#2196F3"))
        if (availableLoans > 0) colors.add(Color.parseColor("#FF9800"))
        
        dataSet.colors = colors
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 11f
        
        dataSet.xValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
        dataSet.yValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
        dataSet.setDrawValues(true)

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = mxnFormat.format(value.toDouble())
        }

        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.description.isEnabled = false
        
        val saldoTotal = totalIngresos - totalGastos
        pieChart.centerText = "Saldo Disponible\n${mxnFormat.format(saldoTotal)}"
        pieChart.setCenterTextSize(14f)
        pieChart.holeRadius = 50f
        pieChart.transparentCircleRadius = 55f
        
        pieChart.setDrawEntryLabels(false) 
        pieChart.setExtraOffsets(5f, 5f, 5f, 5f)

        val legend = pieChart.legend
        legend.isEnabled = true
        legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.isWordWrapEnabled = true
        
        pieChart.setTouchEnabled(false)
        pieChart.invalidate() 
    }

    private fun setupLineChart(lineChart: LineChart) {
        val mxnFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        val expenseEntries = ArrayList<Entry>()
        val incomeEntries = ArrayList<Entry>()
        val debtEntries = ArrayList<Entry>()
        val xLabels = mutableListOf<String>()
        val sdf = SimpleDateFormat("dd/MM", Locale("es", "MX"))
        
        val daysList = mutableListOf<Pair<Long, Long>>() 
        for (i in 6 downTo 0) {
            val dayStart = Calendar.getInstance()
            dayStart.add(Calendar.DAY_OF_YEAR, -i)
            dayStart.set(Calendar.HOUR_OF_DAY, 0)
            dayStart.set(Calendar.MINUTE, 0)
            dayStart.set(Calendar.SECOND, 0)
            dayStart.set(Calendar.MILLISECOND, 0)
            val dayEnd = Calendar.getInstance()
            dayEnd.add(Calendar.DAY_OF_YEAR, -i)
            dayEnd.set(Calendar.HOUR_OF_DAY, 23)
            dayEnd.set(Calendar.MINUTE, 59)
            dayEnd.set(Calendar.SECOND, 59)
            dayEnd.set(Calendar.MILLISECOND, 999)
            daysList.add(Pair(dayStart.timeInMillis, dayEnd.timeInMillis))
        }

        daysList.forEachIndexed { index, (start, end) ->
            val dailyExpense = history.filter { it.tipo == "Gasto" && !it.isDeuda && !it.id.startsWith("recurring_") && it.fecha in start..end }.sumOf { it.monto }
            val dailyIncome = history.filter { it.tipo == "Ingreso" && it.fecha in start..end }.sumOf { it.monto }
            val dailyDebt = history.filter { it.isDeuda && it.fecha in start..end }.sumOf { it.monto }
            
            expenseEntries.add(Entry(index.toFloat(), dailyExpense.toFloat()))
            incomeEntries.add(Entry(index.toFloat(), dailyIncome.toFloat()))
            debtEntries.add(Entry(index.toFloat(), dailyDebt.toFloat()))
            xLabels.add(sdf.format(Date(start)))
        }

        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(createLineDataSet(incomeEntries, "Flujo Caja", "#4CAF50"))
        dataSets.add(createLineDataSet(expenseEntries, "Gastos", "#F44336"))
        dataSets.add(createLineDataSet(debtEntries, "Deudas", "#FF9800"))

        lineChart.data = LineData(dataSets)
        lineChart.description.isEnabled = false
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.granularity = 1f
        lineChart.xAxis.labelCount = 7
        xAxisFormatter(lineChart.xAxis, xLabels)
        lineChart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = mxnFormat.format(value.toDouble())
        }
        lineChart.axisRight.isEnabled = false
        lineChart.setTouchEnabled(false)
        lineChart.invalidate()
    }

    private fun xAxisFormatter(xAxis: XAxis, labels: List<String>) {
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in labels.indices) labels[idx] else ""
            }
        }
    }

    private fun createLineDataSet(entries: List<Entry>, label: String, colorHex: String): LineDataSet {
        val set = LineDataSet(entries, label)
        val color = Color.parseColor(colorHex)
        set.color = color
        set.setCircleColor(color)
        set.lineWidth = 2.5f
        set.circleRadius = 4f
        set.mode = LineDataSet.Mode.CUBIC_BEZIER
        set.setDrawValues(false)
        return set
    }
}
