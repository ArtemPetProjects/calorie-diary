package com.veles.caloriediary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BelarusProductsTest {
    private val json = File("src/main/assets/products_belarus.json").readText()
    private val csv = File("../docs/data/belarusian-products.csv").readLines()

    private val rowRegex = Regex("""\{"name":"([^"]+)","kcal":([\d.]+),"protein":([\d.]+),"fat":([\d.]+),"carbs":([\d.]+)\}""")

    @Test
    fun belarusAssetHasCompleteNutrients() {
        val rows = rowRegex.findAll(json).toList()
        assertTrue(rows.size >= 60)
        assertTrue(rows.all { it.groupValues.drop(2).all { v -> v.toDouble() >= 0.0 } })
    }

    @Test
    fun belarusAssetNamesAreUnique() {
        val names = rowRegex.findAll(json).map { it.groupValues[1] }.toList()
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun belarusAssetHasNoBrokenObjectBoundary() {
        assertFalse(json.contains("}{"))
    }

    @Test
    fun belarusNamesUseParenthesizedBrand() {
        val names = rowRegex.findAll(json).map { it.groupValues[1] }.toList()
        assertTrue(names.all { it.contains('(') && it.endsWith(')') })
    }

    @Test
    fun belarusProductsHaveSourceRows() {
        val header = csv.first()
        assertEquals("name|brand|source_url|source_type|kcal|protein|fat|carbs|checked_on", header)
        val sources = csv.drop(1).map { it.split('|') }
        assertTrue(sources.all { it.size == 9 && it[2].startsWith("http") })
        val names = rowRegex.findAll(json).map { it.groupValues[1] }.toSet()
        assertTrue(sources.map { it[0] }.containsAll(names))
    }
}
