package io.github.xingyingyuzhui.modbusmobile.ui.panel

import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectJson
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 「我的 → 导入」替换工程文件后，已经打开的面板按新文件重读，并丢掉进行中的编辑。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PanelImportReloadTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun project(name: String) = Project("default", name, listOf(Panel("pn1", "主面板")))

    @Test
    fun importedProjectReplacesTheLoadedOneAndEndsEditing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repo = ProjectRepository(tmp.newFolder("projects"))
            repo.save(project("旧工程"))
            val model = PanelViewModel(repo, io = dispatcher)
            advanceUntilIdle()
            assertEquals("旧工程", (model.state.value as PanelUiState.Ready).project.name)
            model.startEdit(null)
            advanceUntilIdle()

            repo.importJson(ProjectJson.encode(project("导入的工程")))
            advanceUntilIdle()

            assertEquals("导入的工程", (model.state.value as PanelUiState.Ready).project.name)
            assertNull(model.edit.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
