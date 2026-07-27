package com.sheen.adb.feature.files

import com.sheen.adb.ui.LocalizedTextRef
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.UiLanguage

enum class FilesStringKey(val semanticKey: String) {
    PATH("files.path"),
    LOADING("files.loading"),
    EMPTY("files.empty"),
    ERROR("files.error"),
    CANCELLED("files.cancelled"),
    DISCONNECTED("files.disconnected"),
    UNSUPPORTED("files.unsupported"),
    OUTCOME_UNKNOWN("files.outcome_unknown"),
    CONFIRMATION("files.confirmation"),
    PROGRESS("files.progress"),
    SUCCEEDED("files.succeeded"),
    TASK_CANCELLED("files.task_cancelled"),
    ENTER_FOLDER("files.enter_folder"),
    DOWNLOAD_FILE("files.download_file"),
    UPLOAD_FILE("files.upload_file"),
    RETRY("files.retry"),
    CANCEL_TASK("files.cancel_task"),
    CLOSE("files.close"),
}

object FilesStrings {
    const val OWNER = "files"

    private val catalogs = mapOf(
        UiLanguage.ZH_CN to mapOf(
            FilesStringKey.PATH to "当前路径",
            FilesStringKey.LOADING to "正在加载目录",
            FilesStringKey.EMPTY to "此目录为空",
            FilesStringKey.ERROR to "无法读取目录",
            FilesStringKey.CANCELLED to "目录加载已取消",
            FilesStringKey.DISCONNECTED to "设备已断开，请重新连接后浏览文件。",
            FilesStringKey.UNSUPPORTED to "当前设备或目标位置不支持此操作",
            FilesStringKey.OUTCOME_UNKNOWN to "结果未知，请保持在当前页面并检查资源状态",
            FilesStringKey.CONFIRMATION to "目标已存在同名文件",
            FilesStringKey.PROGRESS to "正在传输",
            FilesStringKey.SUCCEEDED to "传输完成",
            FilesStringKey.TASK_CANCELLED to "传输已取消",
            FilesStringKey.ENTER_FOLDER to "进入文件夹",
            FilesStringKey.DOWNLOAD_FILE to "下载文件",
            FilesStringKey.UPLOAD_FILE to "上传文件到设备",
            FilesStringKey.RETRY to "重试",
            FilesStringKey.CANCEL_TASK to "取消任务",
            FilesStringKey.CLOSE to "关闭",
        ),
        UiLanguage.EN_US to mapOf(
            FilesStringKey.PATH to "Current path",
            FilesStringKey.LOADING to "Loading directory",
            FilesStringKey.EMPTY to "This directory is empty",
            FilesStringKey.ERROR to "Unable to read directory",
            FilesStringKey.CANCELLED to "Directory loading cancelled",
            FilesStringKey.DISCONNECTED to "The device disconnected. Reconnect to browse files.",
            FilesStringKey.UNSUPPORTED to "This operation is unsupported by the device or destination",
            FilesStringKey.OUTCOME_UNKNOWN to "Result unknown. Stay on this page and check resource state",
            FilesStringKey.CONFIRMATION to "A file with the same name already exists",
            FilesStringKey.PROGRESS to "Transferring",
            FilesStringKey.SUCCEEDED to "Transfer complete",
            FilesStringKey.TASK_CANCELLED to "Transfer cancelled",
            FilesStringKey.ENTER_FOLDER to "Enter folder",
            FilesStringKey.DOWNLOAD_FILE to "Download file",
            FilesStringKey.UPLOAD_FILE to "Upload file to device",
            FilesStringKey.RETRY to "Retry",
            FilesStringKey.CANCEL_TASK to "Cancel task",
            FilesStringKey.CLOSE to "Close",
        ),
    )

    fun requiredKeys(language: UiLanguage): Set<FilesStringKey> = catalogs.getValue(language).keys

    fun text(
        language: UiLanguage,
        key: FilesStringKey,
    ): String = catalogs.getValue(language).getValue(key)

    fun ref(
        key: FilesStringKey,
        rawValue: String,
    ): LocalizedTextRef = LocalizedTextRef(
        owner = OWNER,
        semanticKey = key.semanticKey,
        arguments = listOf(
            TypedTextArgument(
                name = "value",
                value = rawValue,
                type = TextArgumentType.VERBATIM,
            ),
        ),
    )

    fun safeSingleLine(rawValue: String, maxCodePoints: Int = 160): String =
        SafeVerbatimText.render(
            rawValue,
            SafeVerbatimPolicy.SingleLine(maxCodePoints = maxCodePoints),
        ).display

    fun errorKey(
        category: FileTaskErrorCategory,
        technicalCode: String,
    ): FilesStringKey {
        require(technicalCode.isNotBlank())
        return when (category) {
            FileTaskErrorCategory.PROVIDER_UNSUPPORTED,
            FileTaskErrorCategory.UNSUPPORTED_TYPE,
            FileTaskErrorCategory.INTEGRITY_UNAVAILABLE,
            -> FilesStringKey.UNSUPPORTED
            FileTaskErrorCategory.CLEANUP_FAILED,
            FileTaskErrorCategory.STREAM_CLOSED,
            -> FilesStringKey.OUTCOME_UNKNOWN
            else -> FilesStringKey.ERROR
        }
    }

    fun taskTitleKey(status: FileTaskStatus): FilesStringKey = when (status) {
        FileTaskStatus.Preparing,
        is FileTaskStatus.Transferring,
        FileTaskStatus.Verifying,
        FileTaskStatus.Committing,
        -> FilesStringKey.PROGRESS
        FileTaskStatus.AwaitingConflict -> FilesStringKey.CONFIRMATION
        FileTaskStatus.Succeeded -> FilesStringKey.SUCCEEDED
        is FileTaskStatus.Failed -> errorKey(status.error.category, status.error.technicalCode)
        FileTaskStatus.Cancelled -> FilesStringKey.TASK_CANCELLED
        is FileTaskStatus.CleanupFailed -> FilesStringKey.OUTCOME_UNKNOWN
    }
}
