package io.openlist.client.navigation

/**
 * Route constants for the single Activity + Compose Navigation graph.
 * Screens beyond Sprint 1 (Login, FileList, FileDetail, AddInstance) are declared
 * here as reserved route names so navigation call sites compiled in later sprints
 * don't need to touch this file's structure.
 */
object Routes {
    const val SPLASH = "splash"
    const val INSTANCE_LIST = "instance_list"
    const val ADD_INSTANCE = "add_instance"
    const val LOGIN = "login/{instanceId}"
    const val FILE_LIST = "files/{instanceId}?path={path}"
    const val FILE_DETAIL = "file_detail/{instanceId}?path={path}"
    const val SETTINGS = "settings"

    fun login(instanceId: String) = "login/$instanceId"
    fun fileList(instanceId: String, path: String = "/") = "files/$instanceId?path=${encodePathArg(path)}"
    fun fileDetail(instanceId: String, path: String) = "file_detail/$instanceId?path=${encodePathArg(path)}"

    private fun encodePathArg(path: String) =
        java.net.URLEncoder.encode(path, "UTF-8")
}
