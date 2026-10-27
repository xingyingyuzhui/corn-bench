package io.github.xingyingyuzhui.modbusmobile.ui.mine

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.export.ImportPart
import io.github.xingyingyuzhui.modbusmobile.application.export.ImportRead
import io.github.xingyingyuzhui.modbusmobile.application.export.ProjectImport
import io.github.xingyingyuzhui.modbusmobile.application.export.SlaveImport
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 导入预览：列出将替换的内容和警告，按勾选导入；读失败时只给原因，说明没有改动。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class ImportSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val preview = ImportRead.Preview(
        project = ProjectImport("{}", "车间 A", panels = 2, widgets = 5, points = 7, warnings = listOf("组件 w9 类型未知，原样保留")),
        slave = SlaveImport("{}", listOf(1, 2), regions = 3, rules = 1, warnings = emptyList()),
        skipped = listOf("points.csv"),
    )

    @Test
    fun previewListsBothPartsAndImportsTheCheckedOnes() {
        var imported: Set<ImportPart>? = null
        compose.setContent {
            ModbusTheme(UiSettings()) { ImportSheet(preview, busy = false, onImport = { _, parts -> imported = parts }, onDismiss = {}) }
        }
        compose.onNodeWithText("「车间 A」· 2 个面板 · 5 个组件 · 7 个点位").assertIsDisplayed()
        compose.onNodeWithText("Unit 1, 2 · 3 个地址段 · 1 条模拟规则").assertIsDisplayed()
        compose.onNodeWithTag("import-part-PROJECT-warning", useUnmergedTree = true).assertTextContains("原样保留", substring = true)
        compose.onNodeWithTag("import-skipped", useUnmergedTree = true).assertTextContains("points.csv", substring = true)

        compose.onNodeWithTag("import-part-SLAVE").performClick()
        compose.onNodeWithTag("import-confirm").performClick()
        assertEquals(setOf(ImportPart.PROJECT), imported)

        compose.onNodeWithTag("import-part-PROJECT").performClick()
        compose.onNodeWithTag("import-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("import-part-SLAVE").performClick()
        compose.onNodeWithTag("import-confirm").assertIsEnabled()
    }

    @Test
    fun failedReadShowsTheReasonAndNothingChanged() {
        compose.setContent {
            ModbusTheme(UiSettings()) { ImportSheet(ImportRead.Failed("不是有效的 ZIP 文件"), busy = false, onImport = { _, _ -> }, onDismiss = {}) }
        }
        compose.onNodeWithText("不是有效的 ZIP 文件", substring = true).assertIsDisplayed()
        compose.onNodeWithText("当前配置没有任何改动", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("import-confirm").assertDoesNotExist()
    }
}
