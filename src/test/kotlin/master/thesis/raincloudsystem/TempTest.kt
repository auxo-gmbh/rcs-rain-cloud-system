package master.thesis.raincloudsystem

import org.junit.jupiter.api.Test

class TempTest {

    @Test
    fun temp() {
        val list = listOf(0.16806999999999994, 0.16806999999999994, 0.16806999999999994, 0.16806999999999994, 0.3361399999999999)

        val q2 = calculateMedian(list)
        println(q2)
        print(list)
        val q1 = calculateQ1(list, q2)
        println(q1)
        print(list)
        val q3 = calculateQ3(list, q2)
        println(q3)
        print(list)
    }

    private fun calculateMedian(list: List<Double>) = list.let {
        if (it.size % 2 == 0) {
            (it[it.size / 2] + it[(it.size - 1) / 2]) / 2
        } else {
            it[it.size / 2]
        }
    }

    private fun calculateQ1(list: List<Double>, q2: Double): Double = list.let {
        val sublist = if (it.size % 2 == 0) {
            it.subList(0, it.size / 2)
        } else {
            it.subList(0, (it.size / 2) + 1)
        }
        return calculateMedian(sublist)
    }

    private fun calculateQ3(list: List<Double>, q2: Double): Double = list.let {
        val sublist = if (it.size % 2 == 0) {
            it.subList(it.size / 2, it.size)
        } else {
            it.subList((it.size / 2) + 1, it.size)
        }
        return calculateMedian(sublist)
    }
}
