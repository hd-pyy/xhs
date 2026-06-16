package com.xhsdn.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 占位：androidUnitTest 至少要有一个测试文件，否则 AGP 抱怨无 source。
 * 阶段 4 起会加入 Android 端 FileStorage / LivePhotoWriter 的 instrumented 测试。
 */
class PlaceholderTest {
    @Test fun moduleName_isCore() {
        assertEquals("xhsdn-core", MODULE_NAME)
    }

    @Test fun platform_isAndroid() {
        assertEquals("Android", platformName())
    }
}
