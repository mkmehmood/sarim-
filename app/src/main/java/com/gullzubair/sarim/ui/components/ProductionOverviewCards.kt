package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.ProductionEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents

@Composable
private fun SxHero(label: String, value: String, sub: String, chip: String, chipColor: Color) {
    val accents = LocalSarimAccents.current
    Column(Modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = chipColor)
            if (chip.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = chipColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.4f))
                ) {
                    Text(chip, style = MaterialTheme.typography.labelSmall, color = chipColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
    }
}

@Composable
private fun SxSplit(aLabel: String, a: Double, aColor: Color, bLabel: String, b: Double, bColor: Color, fmt: (Double) -> String) {
    val accents = LocalSarimAccents.current
    val total = (a.coerceAtLeast(0.0) + b.coerceAtLeast(0.0))
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(accents.inputFill)) {
            if (total > 0.0) {
                val fa = (a.coerceAtLeast(0.0) / total).toFloat()
                if (fa > 0f) Box(Modifier.weight(fa).height(8.dp).background(aColor))
                if (1f - fa > 0f) Box(Modifier.weight(1f - fa).height(8.dp).background(bColor))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$aLabel ${fmt(a)}", style = MaterialTheme.typography.labelSmall, color = aColor)
            Text("$bLabel ${fmt(b)}", style = MaterialTheme.typography.labelSmall, color = bColor)
        }
    }
}

@Composable
private fun SxTile(label: String, value: String, color: Color, modifier: Modifier = Modifier, sub: String = "") {
    val accents = LocalSarimAccents.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accents.inputFill.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
    }
}

@Composable
private fun SxTileRow(content: @Composable (Modifier) -> Unit, second: (@Composable (Modifier) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content(Modifier.weight(1f))
        if (second != null) second(Modifier.weight(1f))
    }
}

@Composable
private fun SxBreakdown(entries: List<Pair<String, Double>>) {
    val accents = LocalSarimAccents.current
    if (entries.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("SOLD BY CUSTOMER (${entries.size})", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
        entries.take(8).forEach { (name, qty) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                Text(InputGuards.formatKg(qty), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = accents.gold)
            }
        }
        if (entries.size > 8) Text("+${entries.size - 8} more", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
    }
}

@Composable
fun StoreOverviewCard(o: ProductionEngine.StoreOverview, period: ProductionEngine.Period, currency: String, masked: Boolean) {
    val accents = LocalSarimAccents.current
    val money = { v: Double -> InputGuards.formatMoney(v, currency, masked) }
    val kg = { v: Double -> InputGuards.formatKg(v) }
    SarimCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(o.store.name, style = MaterialTheme.typography.titleMedium, color = accents.emerald)
            Text(period.chip, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        SxHero(
            "Remaining stock", kg(o.remaining),
            "${kg(o.totalIn)} in · ${kg(o.sold)} sold",
            if (o.totalIn > 0.0) "${o.leftPct}% left" else "",
            if (o.remaining > 0.0) accents.emerald else accents.gold
        )
        Spacer(Modifier.height(8.dp))
        SxSplit("Sold", o.sold, accents.gold, "Remaining", o.remaining.coerceAtLeast(0.0), accents.emerald, kg)
        Spacer(Modifier.height(8.dp))
        SxTileRow({ SxTile("Produced", kg(o.production), accents.emerald, it) }, {
            if (o.returns > 0.0) SxTile("Returns received", kg(o.returns), accents.gold, it)
            else SxTile("Sold (Sales tab)", kg(o.sold), accents.danger, it)
        })
        if (o.returns > 0.0) {
            Spacer(Modifier.height(8.dp))
            SxTile("Sold (Sales tab)", kg(o.sold), accents.danger, Modifier.fillMaxWidth())
        }
        if (o.soldByCustomer.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SxBreakdown(o.soldByCustomer)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = accents.borderSubtle)
        Spacer(Modifier.height(8.dp))
        SxTileRow({ SxTile("Formula units", InputGuards.fmtNum(o.formulaUnits), accents.gold, it) }, { SxTile("Formula cost", money(o.formulaCost), accents.danger, it) })
        Spacer(Modifier.height(8.dp))
        SxTileRow({ SxTile("Total value", money(o.value), accents.emerald, it) }, {
            SxTile("Net profit", money(o.profit), accents.emerald, it, if (!masked) o.marginPct?.let { m -> "$m% margin" }.orEmpty() else "")
        })
    }
}

@Composable
fun CombinedOverviewCard(t: ProductionEngine.CombinedOverview, period: ProductionEngine.Period, currency: String, masked: Boolean) {
    val accents = LocalSarimAccents.current
    val money = { v: Double -> InputGuards.formatMoney(v, currency, masked) }
    val kg = { v: Double -> InputGuards.formatKg(v) }
    SarimCard(accentBorder = accents.emerald) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total combined", style = MaterialTheme.typography.titleMedium, color = accents.emerald)
            Text(period.chip, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        SxHero(
            "Net profit", money(t.profit),
            "on ${money(t.value)} total value",
            if (!masked) t.marginPct?.let { "$it% margin" }.orEmpty() else "",
            if ((t.marginPct ?: 0) >= 0) accents.emerald else accents.danger
        )
        Spacer(Modifier.height(8.dp))
        SxSplit("Cost", t.cost, accents.danger, "Profit", t.profit.coerceAtLeast(0.0), accents.emerald, money)
        Spacer(Modifier.height(8.dp))
        SxTileRow({ SxTile("Total value", money(t.value), accents.emerald, it) }, { SxTile("Total cost", money(t.cost), accents.danger, it) })
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = accents.borderSubtle)
        Spacer(Modifier.height(8.dp))
        SxHero("Total remaining", kg(t.remaining), "${kg(t.qty)} in · ${kg(t.sold)} sold", "", accents.emerald)
        Spacer(Modifier.height(8.dp))
        SxSplit("Sold", t.sold, accents.gold, "Remaining", t.remaining.coerceAtLeast(0.0), accents.emerald, kg)
        Spacer(Modifier.height(8.dp))
        SxTileRow({ SxTile("Fresh production", kg(t.production), accents.emerald, it) }, { SxTile("Total sold", kg(t.sold), accents.danger, it) })
        if (t.returns > 0.0) {
            Spacer(Modifier.height(8.dp))
            SxTile("Total returns", kg(t.returns), accents.gold, Modifier.fillMaxWidth())
        }
        if (t.soldByCustomer.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SxBreakdown(t.soldByCustomer)
        }
        Spacer(Modifier.height(8.dp))
        SxTileRow({ SxTile("Formula units", InputGuards.fmtNum(t.formulaUnits), accents.gold, it) }, { SxTile("Formula cost", money(t.formulaCost), accents.danger, it) })
    }
}
