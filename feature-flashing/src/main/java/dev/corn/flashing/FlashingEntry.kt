package dev.corn.flashing

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.corn.flashing.ui.CornApp
import dev.corn.flashing.ui.FlashViewModel

/**
 * 烧录模式的入口 Composable（原 Corn Flashing MainActivity 的 setContent 内容）。
 * ViewModel 挂在调用方的 ViewModelStoreOwner 上：外壳把烧录子图出栈时它随之清掉。
 * 系统栏样式（深色、透明、不强制对比）由外壳 Activity 统一设置。
 */
@Composable
fun FlashingEntry() {
    val vm: FlashViewModel = viewModel()
    CornApp(vm)
}
