package io.github.xingyingyuzhui.modbusmobile.application

/**
 * Corn Modbus 的署名。界面里的“by xingyingyuzhui”、导出 ZIP 的注释、BuildConfig 和
 * manifest 的 meta-data 都从这里来；以 PolyForm Noncommercial 1.0.0 授权，不得用于商业用途。
 *
 * @author xingyingyuzhui (https://github.com/xingyingyuzhui)
 */
object Authorship {
    const val APP = "Corn Modbus"
    const val AUTHOR = "xingyingyuzhui"
    const val AUTHOR_URL = "https://github.com/xingyingyuzhui"
    const val REPO_URL = "https://github.com/xingyingyuzhui/corn-modbus"
    const val LICENSE = "PolyForm-Noncommercial-1.0.0"

    /** 一行完整署名：写进每个导出 ZIP 的注释，解压工具里能看到。 */
    const val SIGNATURE = "$APP · by $AUTHOR · $REPO_URL · $LICENSE"
}
