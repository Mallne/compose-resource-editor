package cloud.mallne.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

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
        val result = mutableListOf<ComposeResourcesRoot>()

        val scope = GlobalSearchScope.projectScope(project)
        val composeDirs = FilenameIndex.getVirtualFilesByName(
            "composeResources", scope
        )

        for (root in composeDirs) {
            val dirs = root.children
                .filter { it.isDirectory && it.name.startsWith("values") }
                .map { dir ->
                    val locale = if (dir.name == "values") null
                    else dir.name.removePrefix("values-")
                    val stringsXml = dir.children.find {
                        !it.isDirectory && it.name == "strings.xml"
                    }
                    LocaleDir(locale, dir, stringsXml)
                }
                .sortedBy { it.locale ?: "" }

            if (dirs.isNotEmpty()) {
                result.add(ComposeResourcesRoot(root, dirs))
            }
        }

        return result
    }
}
