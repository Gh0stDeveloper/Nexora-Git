package com.nexora.git.core.templates

import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectTemplateManager @Inject constructor(
    private val catalog: ProjectTemplateCatalog,
    private val workspaceRegistry: WorkspaceRegistry,
) {
    val templates: List<ProjectTemplateSummary>
        get() = catalog.templates

    suspend fun create(
        templateId: String,
        projectName: String,
    ): Workspace {
        val rendered = catalog.render(templateId, projectName)
        return workspaceRegistry.createGeneratedWorkspace(
            name = rendered.projectName,
            files = rendered.files,
        ).workspace
    }
}
