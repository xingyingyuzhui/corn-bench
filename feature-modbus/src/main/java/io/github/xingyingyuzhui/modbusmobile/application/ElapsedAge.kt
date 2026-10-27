package io.github.xingyingyuzhui.modbusmobile.application

/**
 * “多久之前”的显示单位：1 分钟内用秒，1 小时内用分钟，1 天内用小时，再久用天。
 * 写入确认里的“来自 N 秒前的读取”在值很旧时会变成几十万秒，读不出有多旧。
 */
enum class AgeUnit { SECONDS, MINUTES, HOURS, DAYS }

data class ElapsedAge(val amount: Long, val unit: AgeUnit) {
    companion object {
        /** 负数（时钟回拨）按 0 秒处理，取整都是向下取。 */
        fun of(elapsedMs: Long): ElapsedAge {
            val s = (elapsedMs / 1000L).coerceAtLeast(0L)
            return when {
                s < 60L -> ElapsedAge(s, AgeUnit.SECONDS)
                s < 3_600L -> ElapsedAge(s / 60L, AgeUnit.MINUTES)
                s < 86_400L -> ElapsedAge(s / 3_600L, AgeUnit.HOURS)
                else -> ElapsedAge(s / 86_400L, AgeUnit.DAYS)
            }
        }
    }
}
