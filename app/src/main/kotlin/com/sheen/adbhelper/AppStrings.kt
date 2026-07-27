package com.sheen.adbhelper

import com.sheen.adb.ui.LocalizedTextRef
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.UiLanguage

enum class AppStringKey(val semanticKey: String) {
    NAVIGATION_LOCKED("app.navigation.locked"),
    NAVIGATION_DISABLED_REASON("app.navigation.disabled_reason"),
    LONG_TASK_BACK_TITLE("app.long_task.back_title"),
    CONTINUE_TASK("app.long_task.continue"),
    CANCEL_TASK_AND_LEAVE("app.long_task.cancel_and_leave"),
    ROOT_OVERLAY_CLOSE("app.overlay.close"),
    PAIRING_OVERLAY_DISMISS("app.pairing.dismiss"),
    CONNECT_FIRST("app.connect_first"),
    PAGE_LOADING_WAIT("app.page_loading.wait"),
    PAGE_LOADING_CANCEL("app.page_loading.cancel"),
    PAGE_LOADING_RECOVERING("app.page_loading.recovering"),
}

object AppStrings {
    const val OWNER = "app"

    private val placeholderSchemas: Map<AppStringKey, Map<String, TextArgumentType>> = mapOf(
        AppStringKey.NAVIGATION_LOCKED to mapOf("task" to TextArgumentType.TEXT),
        AppStringKey.NAVIGATION_DISABLED_REASON to mapOf("task" to TextArgumentType.TEXT),
        AppStringKey.LONG_TASK_BACK_TITLE to mapOf("task" to TextArgumentType.TEXT),
        AppStringKey.CONTINUE_TASK to emptyMap(),
        AppStringKey.CANCEL_TASK_AND_LEAVE to emptyMap(),
        AppStringKey.ROOT_OVERLAY_CLOSE to emptyMap(),
        AppStringKey.PAIRING_OVERLAY_DISMISS to emptyMap(),
        AppStringKey.CONNECT_FIRST to emptyMap(),
        AppStringKey.PAGE_LOADING_WAIT to emptyMap(),
        AppStringKey.PAGE_LOADING_CANCEL to emptyMap(),
        AppStringKey.PAGE_LOADING_RECOVERING to emptyMap(),
    )

    private val catalogs: Map<UiLanguage, Map<AppStringKey, String>> = mapOf(
        UiLanguage.ZH_CN to mapOf(
            AppStringKey.NAVIGATION_LOCKED to "{task}正在执行，暂时不能切换页面。",
            AppStringKey.NAVIGATION_DISABLED_REASON to "请先完成或取消{task}。",
            AppStringKey.LONG_TASK_BACK_TITLE to "{task}仍在执行",
            AppStringKey.CONTINUE_TASK to "继续任务",
            AppStringKey.CANCEL_TASK_AND_LEAVE to "取消任务并离开",
            AppStringKey.ROOT_OVERLAY_CLOSE to "关闭浮层",
            AppStringKey.PAIRING_OVERLAY_DISMISS to "关闭配对",
            AppStringKey.CONNECT_FIRST to "请先连接设备",
            AppStringKey.PAGE_LOADING_WAIT to "正在完成当前页面加载，完成后自动切换",
            AppStringKey.PAGE_LOADING_CANCEL to "取消加载并切换",
            AppStringKey.PAGE_LOADING_RECOVERING to "正在安全结束当前加载…",
        ),
        UiLanguage.EN_US to mapOf(
            AppStringKey.NAVIGATION_LOCKED to "{task} is running. You cannot change pages yet.",
            AppStringKey.NAVIGATION_DISABLED_REASON to "Finish or cancel {task} first.",
            AppStringKey.LONG_TASK_BACK_TITLE to "{task} is still running",
            AppStringKey.CONTINUE_TASK to "Continue task",
            AppStringKey.CANCEL_TASK_AND_LEAVE to "Cancel task and leave",
            AppStringKey.ROOT_OVERLAY_CLOSE to "Close overlay",
            AppStringKey.PAIRING_OVERLAY_DISMISS to "Close pairing",
            AppStringKey.CONNECT_FIRST to "Connect a device first",
            AppStringKey.PAGE_LOADING_WAIT to "Finishing this page load, then switching automatically",
            AppStringKey.PAGE_LOADING_CANCEL to "Cancel load and switch",
            AppStringKey.PAGE_LOADING_RECOVERING to "Safely finishing the current load…",
        ),
    )

    fun requiredKeys(language: UiLanguage): Set<AppStringKey> = catalogs.getValue(language).keys

    fun placeholderSchema(
        language: UiLanguage,
        key: AppStringKey,
    ): Map<String, TextArgumentType> {
        require(catalogs.getValue(language).containsKey(key))
        return placeholderSchemas.getValue(key)
    }

    fun ref(
        key: AppStringKey,
        vararg arguments: TypedTextArgument,
    ): LocalizedTextRef = LocalizedTextRef(
        owner = OWNER,
        semanticKey = key.semanticKey,
        arguments = arguments.toList(),
    )

    fun resolve(language: UiLanguage, ref: LocalizedTextRef): String {
        require(ref.owner == OWNER)
        val key = AppStringKey.entries.firstOrNull { it.semanticKey == ref.semanticKey }
            ?: error("Unknown App semantic key")
        val schema = placeholderSchemas.getValue(key)
        val arguments = ref.arguments.associateBy(TypedTextArgument::name)
        require(arguments.keys == schema.keys)
        schema.forEach { (name, type) -> require(arguments.getValue(name).type == type) }
        return ref.arguments.fold(catalogs.getValue(language).getValue(key)) { text, argument ->
            val value = when (argument.type) {
                TextArgumentType.VERBATIM -> SafeVerbatimText.render(
                    argument.value as String,
                    SafeVerbatimPolicy.SingleLine(maxCodePoints = 120),
                ).display
                else -> argument.value.toString()
            }
            text.replace("{${argument.name}}", value)
        }
    }
}
