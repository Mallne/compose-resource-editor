package cloud.mallne.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

data class LocaleDir(
    val locale: String?, // null for base "values"
    val dir: VirtualFile,
    val stringsXml: VirtualFile?
)

data class ComposeResourcesRoot(
    val root: VirtualFile,
    val localeDirs: List<LocaleDir>
)

object ComposeResourcesScanner {

    fun scan(project: Project): List<ComposeResourcesRoot> {
        return ApplicationManager.getApplication().runReadAction(
            Computable {
                val result = mutableListOf<ComposeResourcesRoot>()
                val projectDir = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return@Computable result
                val skipped = hashSetOf("build", ".gradle", ".idea", "node_modules", ".git", "out", "target", "bin")
                visitDir(projectDir, result, skipped)
                result
            }
        )
    }

    private fun visitDir(dir: VirtualFile, result: MutableList<ComposeResourcesRoot>, skipped: Set<String>) {
        if (!dir.isDirectory) return

        if (dir.name == "composeResources") {
            val dirs = dir.children
                .filter { it.isDirectory && it.name.startsWith("values") }
                .map { child ->
                    val locale = if (child.name == "values") null
                    else child.name.removePrefix("values-")
                    val stringsXml = child.children.find {
                        !it.isDirectory && it.name == "strings.xml"
                    }
                    LocaleDir(locale, child, stringsXml)
                }
                .sortedBy { it.locale ?: "" }

            if (dirs.isNotEmpty()) {
                result.add(ComposeResourcesRoot(dir, dirs))
            }
            return
        }

        if (dir.name in skipped) return

        for (child in dir.children) {
            visitDir(child, result, skipped)
        }
    }
}
