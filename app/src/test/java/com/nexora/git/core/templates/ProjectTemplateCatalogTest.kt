package com.nexora.git.core.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTemplateCatalogTest {

    private val catalog = ProjectTemplateCatalog()

    @Test
    fun rendersAllSupportedTemplateFamilies() {
        assertEquals(4, catalog.templates.size)
        assertTrue(catalog.templates.any { it.id == "android-compose" })
        assertTrue(catalog.templates.any { it.id == "typescript-node" })
        assertTrue(catalog.templates.any { it.id == "python-cli" })
    }

    @Test
    fun substitutesProjectAndPackageNames() {
        val rendered = catalog.render(
            templateId = "android-compose",
            projectName = "Nexora Demo",
        )

        assertEquals("Nexora Demo", rendered.projectName)
        assertTrue(
            rendered.files.keys.any {
                it.contains("nexorademo")
            },
        )
        assertTrue(
            rendered.files.values.none {
                it.contains("{{PROJECT_NAME}}") ||
                    it.contains("{{PACKAGE_NAME}}")
            },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPathLikeProjectNames() {
        catalog.render(
            templateId = "kotlin-cli",
            projectName = "../escape",
        )
    }
}
