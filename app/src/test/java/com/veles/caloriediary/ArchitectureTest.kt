package com.veles.caloriediary

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ArchitectureTest {

    private val forbidden = listOf(
        "import android.",
        "import androidx.",
        "android.content.",
        "android.database.",
        "android.app.",
        "android.view.",
        "android.widget.",
        "android.os."
    )

    @Test
    fun domainHasNoAndroidDependencies() {
        val domainDir = resolveDomainDir()
        assertTrue("Пакет domain должен существовать: ${domainDir.path}", domainDir.exists())
        val files = domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("В domain должны быть Kotlin-файлы", files.isNotEmpty())
        val violations = mutableListOf<String>()
        for (file in files) {
            val text = file.readText()
            for (f in forbidden) {
                if (text.contains(f)) {
                    violations.add("${file.name}: содержит '$f'")
                }
            }
            // domain не должен зависеть от data/ui/Db
            for (f in listOf("import com.veles.caloriediary.Db", "import com.veles.caloriediary.MainActivity", "import com.veles.caloriediary.DayView", "import com.veles.caloriediary.ProductsView", "import com.veles.caloriediary.ProfileView", "import com.veles.caloriediary.DishDialog")) {
                if (text.contains(f)) {
                    violations.add("${file.name}: содержит '$f'")
                }
            }
        }
        if (violations.isNotEmpty()) {
            fail("Нарушения зависимостей domain:\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun domainContainsRepositoryInterfaces() {
        val domainDir = resolveDomainDir()
        val texts = domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }.toList()
        val hasRepo = texts.any { it.contains("interface ") && it.contains("Repository") }
        assertTrue("В domain должен быть интерфейс репозитория", hasRepo)
    }

    @Test
    fun domainContainsUseCases() {
        val domainDir = resolveDomainDir()
        val texts = domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }.toList()
        val hasUseCase = texts.any { it.contains("UseCase") || it.contains("class ") && it.contains("UseCase") }
        assertTrue("В domain должны быть use case классы", hasUseCase)
    }

    private fun resolveDomainDir(): File {
        val candidates = listOf(
            File("src/main/java/com/veles/caloriediary/domain"),
            File("app/src/main/java/com/veles/caloriediary/domain"),
            File("../app/src/main/java/com/veles/caloriediary/domain")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }
}
