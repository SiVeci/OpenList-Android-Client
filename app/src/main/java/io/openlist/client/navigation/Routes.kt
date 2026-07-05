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
    const val SHARE_LIST = "share_list/{instanceId}"
    const val SHARE_DETAIL = "share_detail/{instanceId}/{shareId}"
    const val SEARCH = "search/{instanceId}?path={path}"
    const val TASK_CENTER = "task_center/{instanceId}"
    const val PREVIEW = "preview/{instanceId}?path={path}"
    const val MEDIA_PLAYER = "player/{instanceId}?path={path}"
    /** Single sprint-wide host route for the whole admin console (v0.5_EXECUTION_PLAN.md
     * §5.4/§9.2): the 7 admin sections are Tabs inside one host screen, not one route each,
     * to avoid navigation-graph bloat. `tab` is optional and only used for deep-linking
     * into a specific Tab (e.g. a "no search index" error state jumping straight to the
     * Index tab) — same optional-query-param style as [FILE_LIST]/[SEARCH]. */
    const val ADMIN = "admin/{instanceId}?tab={tab}"

    fun login(instanceId: String) = "login/$instanceId"
    fun fileList(instanceId: String, path: String = "/") = "files/$instanceId?path=${encodePathArg(path)}"
    fun fileDetail(instanceId: String, path: String) = "file_detail/$instanceId?path=${encodePathArg(path)}"
    fun shareList(instanceId: String) = "share_list/$instanceId"
    fun shareDetail(instanceId: String, shareId: String) = "share_detail/$instanceId/$shareId"
    fun search(instanceId: String, path: String = "/") = "search/$instanceId?path=${encodePathArg(path)}"
    fun taskCenter(instanceId: String) = "task_center/$instanceId"
    fun preview(instanceId: String, path: String) = "preview/$instanceId?path=${encodePathArg(path)}"
    fun mediaPlayer(instanceId: String, path: String) = "player/$instanceId?path=${encodePathArg(path)}"
    fun admin(instanceId: String, tab: String? = null) =
        "admin/$instanceId" + (tab?.let { "?tab=${encodePathArg(it)}" } ?: "")

    private fun encodePathArg(path: String) =
        java.net.URLEncoder.encode(path, "UTF-8")
}
