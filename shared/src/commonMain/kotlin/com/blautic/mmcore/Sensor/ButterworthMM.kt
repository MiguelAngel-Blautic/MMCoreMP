package Sensor

class ButterworthMM {
    private val iir: Butterworth = Butterworth()
    fun lowPass(order: Int, sampleRate: Double, cutoffFrequency: Double) {
        iir.lowPass(order, sampleRate, cutoffFrequency)
    }

    fun highPass(order: Int, sampleRate: Double, cutoffFrequency: Double) {
        iir.highPass(order, sampleRate, cutoffFrequency)
    }

    fun bandPass(order: Int, sampleRate: Double, centerFrequency: Double, widthFrequency: Double) {
        iir.bandPass(order, sampleRate, centerFrequency, widthFrequency)
    }

    fun bandStop(order: Int, sampleRate: Double, centerFrequency: Double, widthFrequency: Double) {
        iir.bandStop(order, sampleRate, centerFrequency, widthFrequency)
    }

    fun reset() {
        iir.reset()
    }

    fun filter(value: Double): Double {
        return iir.filter(value)
    }
}