package Sensor

import io.ktor.utils.io.core.ByteOrder
import kotlin.math.pow

class BleBytesParser constructor(
    private var mValue: ByteArray,
    offset: Int = 0,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
) {
    /**
     * Get the value of the internal offset
     */
    /**
     * Set the value of the internal offset
     */
    var offset = 0
    /**
     * Get the byte array
     *
     * @return the complete byte array
     */

    /**
     * Get the set byte order
     */
    val byteOrder: ByteOrder

    /**
     * Return an Integer value of the specified type. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte(s) value
     * @return An Integer object or null in case the byte array was not valid
     */
    fun getIntValue(formatType: Int): Int {
        val result = getIntValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    /**
     * Return an Integer value of the specified type and specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType the format type used to interpret the byte(s) value
     * @return an Integer object or null in case the byte array was not valid
     */
    fun getIntValue(formatType: Int, byteOrder: ByteOrder): Int {
        val result = getIntValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    fun getValue(): ByteArray {
        return mValue
    }

    /**
     * Return a Long value. This operation will automatically advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    val longValue: Long
        get() = getLongValue(byteOrder)

    /**
     * Return a Long value using the specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    fun getLongValue(byteOrder: ByteOrder): Long {
        val result = getLongValue(offset, byteOrder)
        offset += 8
        return result
    }

    /**
     * Return a Long value using the specified byte order and offset position. This operation will not advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    fun getLongValue(offset: Int, byteOrder: ByteOrder): Long {
        return if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            var value = (0x00FF and mValue[offset + 7].toInt()).toLong()
            for (i in 6 downTo 0) {
                value = value shl 8

                value += (0x00FF and mValue[i + offset].toInt()).toLong()
            }
            value
        } else {
            var value = (0x00FF and mValue[offset].toInt()).toLong()
            for (i in 1..7) {
                value = value shl 8
                value += (0x00FF and mValue[i + offset].toInt()).toLong()
            }
            value
        }
    }

    /**
     * Return an Integer value of the specified type. This operation will not advance the internal offset to the next position.
     *
     *
     * The formatType parameter determines how the byte array
     * is to be interpreted. For example, settting formatType to
     * [.FORMAT_UINT16] specifies that the first two bytes of the
     * byte array at the given offset are interpreted to generate the
     * return value.
     *
     * @param formatType The format type used to interpret the byte array.
     * @param offset     Offset at which the integer value can be found.
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return Cached value of the byte array or null of offset exceeds value size.
     */
    fun getIntValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Int {
        if (offset + getTypeLen(formatType) > mValue.size) return 0
        return when (formatType) {
            FORMAT_UINT8 -> unsignedByteToInt(mValue[offset])
            FORMAT_UINT16 -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) unsignedBytesToInt(
                mValue[offset], mValue[offset + 1]
            ) else unsignedBytesToInt(mValue[offset + 1], mValue[offset])
            FORMAT_UINT32 -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) unsignedBytesToInt(
                mValue[offset], mValue[offset + 1],
                mValue[offset + 2], mValue[offset + 3]
            ) else unsignedBytesToInt(
                mValue[offset + 3], mValue[offset + 2],
                mValue[offset + 1], mValue[offset]
            )
            FORMAT_SINT8 -> unsignedToSigned(unsignedByteToInt(mValue[offset]), 8)
            FORMAT_SINT16 -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) unsignedToSigned(
                unsignedBytesToInt(
                    mValue[offset],
                    mValue[offset + 1]
                ), 16
            ) else unsignedToSigned(
                unsignedBytesToInt(
                    mValue[offset + 1],
                    mValue[offset]
                ), 16
            )
            FORMAT_SINT32 -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) unsignedToSigned(
                unsignedBytesToInt(
                    mValue[offset],
                    mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]
                ), 32
            ) else unsignedToSigned(
                unsignedBytesToInt(
                    mValue[offset + 3],
                    mValue[offset + 2], mValue[offset + 1], mValue[offset]
                ), 32
            )
            else -> 0
        }
    }

    /**
     * Return a float value of the specified format. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @return The float value at the position of the internal offset
     */
    fun getFloatValue(formatType: Int): Float? {
        val result = getFloatValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    /**
     * Return a float value of the specified format and byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return The float value at the position of the internal offset
     */
    fun getFloatValue(formatType: Int, byteOrder: ByteOrder): Float? {
        val result = getFloatValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    /**
     * Return a float value of the specified format, offset and byte order. This operation will not advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return The float value at the position of the internal offset
     */
    fun getFloatValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Float {
        if (offset + getTypeLen(formatType) > mValue.size) return 0.0f
        return when (formatType) {
            FORMAT_SFLOAT -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) bytesToFloat(
                mValue[offset], mValue[offset + 1]
            ) else bytesToFloat(mValue[offset + 1], mValue[offset])
            FORMAT_FLOAT -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) bytesToFloat(
                mValue[offset], mValue[offset + 1],
                mValue[offset + 2], mValue[offset + 3]
            ) else bytesToFloat(
                mValue[offset + 3], mValue[offset + 2],
                mValue[offset + 1], mValue[offset]
            )
            else -> 0.0f
        }
    }

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @return String value representated by the byte array
     */
    @ExperimentalStdlibApi
    val stringValue: String
        get() = getStringValue(offset)

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @param offset Offset at which the string value can be found.
     * @return String value representated by the byte array
     */
    @ExperimentalStdlibApi
    fun getStringValue(offset: Int): String {
        // Check if there are enough bytes to parse

        // Check if there are enough bytes to parse
        if (offset > mValue.size) return ""

        // Copy all bytes

        // Copy all bytes
        val strBytes = ByteArray(mValue.size - offset)
        for (i in 0 until mValue.size - offset) strBytes[i] = mValue[offset + i]

        // Get rid of trailing zero/space bytes

        // Get rid of trailing zero/space bytes
        var j = strBytes.size
        while (j > 0 && (strBytes[j - 1].toInt() == 0 || strBytes[j - 1].toInt() == 0x20)) j--

        // Convert to string

        // Convert to string
        return strBytes.decodeToString(0, j)
    }


    /*
     * Read bytes and return the ByteArray of the length passed in.  This will increment the offset
     *
     * @return The DateTime read from the bytes. This will cause an exception if bytes run past end. Will return 0 epoch if unparsable
     */
    fun getByteArray(length: Int): ByteArray {
        val array = mValue.copyOfRange(offset, offset + length)
        offset += length
        return array
    }


    /**
     * Set the locally stored value of this byte array
     *
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @param offset     Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    fun setIntValue(value: Int, formatType: Int, offset: Int): Boolean {
        var value = value
        var offset = offset
        prepareArray(offset + getTypeLen(formatType))
        when (formatType) {
            FORMAT_SINT8 -> {
                value = intToSignedBits(value, 8)
                mValue[offset] = (value and 0xFF).toByte()
            }
            FORMAT_UINT8 -> mValue[offset] = (value and 0xFF).toByte()
            FORMAT_SINT16 -> {
                value = intToSignedBits(value, 16)
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    mValue[offset++] = (value and 0xFF).toByte()
                    mValue[offset] = (value shr 8 and 0xFF).toByte()
                } else {
                    mValue[offset++] = (value shr 8 and 0xFF).toByte()
                    mValue[offset] = (value and 0xFF).toByte()
                }
            }
            FORMAT_UINT16 -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                mValue[offset++] = (value and 0xFF).toByte()
                mValue[offset] = (value shr 8 and 0xFF).toByte()
            } else {
                mValue[offset++] = (value shr 8 and 0xFF).toByte()
                mValue[offset] = (value and 0xFF).toByte()
            }
            FORMAT_SINT32 -> {
                value = intToSignedBits(value, 32)
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    mValue[offset++] = (value and 0xFF).toByte()
                    mValue[offset++] = (value shr 8 and 0xFF).toByte()
                    mValue[offset++] = (value shr 16 and 0xFF).toByte()
                    mValue[offset] = (value shr 24 and 0xFF).toByte()
                } else {
                    mValue[offset++] = (value shr 24 and 0xFF).toByte()
                    mValue[offset++] = (value shr 16 and 0xFF).toByte()
                    mValue[offset++] = (value shr 8 and 0xFF).toByte()
                    mValue[offset] = (value and 0xFF).toByte()
                }
            }
            FORMAT_UINT32 -> if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                mValue[offset++] = (value and 0xFF).toByte()
                mValue[offset++] = (value shr 8 and 0xFF).toByte()
                mValue[offset++] = (value shr 16 and 0xFF).toByte()
                mValue[offset] = (value shr 24 and 0xFF).toByte()
            } else {
                mValue[offset++] = (value shr 24 and 0xFF).toByte()
                mValue[offset++] = (value shr 16 and 0xFF).toByte()
                mValue[offset++] = (value shr 8 and 0xFF).toByte()
                mValue[offset] = (value and 0xFF).toByte()
            }
            else -> return false
        }
        return true
    }

    /**
     * Set byte array to an Integer with specified format.
     *
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @return true if the locally stored value has been set
     */
    open fun setIntValue(value: Int, formatType: Int): Boolean {
        val result = setIntValue(value, formatType, offset)
        if (result) {
            offset += getTypeLen(formatType)
        }
        return result
    }


    /**
     * Set byte array to a long
     *
     * @param value New long value for this byte array
     * @return true if the locally stored value has been set
     */
    fun setLong(value: Long): Boolean {
        return setLong(value, offset)
    }

    /**
     * Set byte array to a long
     *
     * @param value  New long value for this byte array
     * @param offset Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    fun setLong(value: Long, offset: Int): Boolean {
        var value = value
        prepareArray(offset + 8)
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            for (i in 7 downTo 0) {
                mValue[i + offset] = (value and 0xFF).toByte()
                value = value shr 8
            }
        } else {
            for (i in 0..7) {
                mValue[i + offset] = (value and 0xFF).toByte()
                value = value shr 8
            }
        }
        return true
    }

    /**
     * Set byte array to a float of the specified type.
     *
     * @param mantissa   Mantissa for this float value
     * @param exponent   exponent value for this float value
     * @param formatType Float format type used to transform the value parameter
     * @param offset     Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    open fun setFloatValue(mantissa: Int, exponent: Int, formatType: Int, offset: Int): Boolean {
        var mantissa = mantissa
        var exponent = exponent
        var offset = offset
        prepareArray(offset + getTypeLen(formatType))
        when (formatType) {
            FORMAT_SFLOAT -> {
                mantissa = intToSignedBits(mantissa, 12)
                exponent = intToSignedBits(exponent, 4)
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    mValue[offset++] = (mantissa and 0xFF).toByte()
                    mValue[offset] = (mantissa shr 8 and 0x0F).toByte()
                    (mValue[offset].plus(exponent and 0x0F shl 4).toByte())
                } else {
                    mValue[offset] = (mantissa shr 8 and 0x0F).toByte()
                    (mValue[offset++].plus(exponent and 0x0F shl 4).toByte())
                    mValue[offset] = (mantissa and 0xFF).toByte()
                }
            }
            FORMAT_FLOAT -> {
                mantissa = intToSignedBits(mantissa, 24)
                exponent = intToSignedBits(exponent, 8)
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    mValue[offset++] = (mantissa and 0xFF).toByte()
                    mValue[offset++] = (mantissa shr 8 and 0xFF).toByte()
                    mValue[offset++] = (mantissa shr 16 and 0xFF).toByte()
                    (mValue[offset].plus(exponent and 0xFF).toByte())
                } else {
                    (mValue[offset++].plus(exponent and 0xFF).toByte())
                    mValue[offset++] = (mantissa shr 16 and 0xFF).toByte()
                    mValue[offset++] = (mantissa shr 8 and 0xFF).toByte()
                    mValue[offset] = (mantissa and 0xFF).toByte()
                }
            }
            else -> return false
        }
        return true
    }

    /**
     * Create byte[] value from Float usingg a given precision, i.e. number of digits after the comma
     *
     * @param value     Float value to create byte[] from
     * @param precision number of digits after the comma to use
     * @return true if the locally stored value has been set
     */
    fun setFloatValue(value: Float, precision: Int): Boolean {
        val mantissa = (value * 10.0.pow(precision.toDouble())).toFloat()
        return setFloatValue(mantissa.toInt(), -precision, FORMAT_FLOAT, offset)
    }

    /**
     * Set byte array to a string at current offset
     *
     * @param value String to be added to byte array
     * @return true if the locally stored value has been set
     */
    fun setString(value: String?): Boolean {
        if (value != null) {
            setString(value, offset)
            offset += value.encodeToByteArray().size
            return true
        }
        return false
    }

    /**
     * Set byte array to a string at specified offset position
     *
     * @param value  String to be added to byte array
     * @param offset the offset to place the string at
     * @return true if the locally stored value has been set
     */
    fun setString(value: String?, offset: Int): Boolean {
        if (value != null) {
            prepareArray(offset + value.length)
            val valueBytes: ByteArray = value.encodeToByteArray()
            valueBytes.copyInto(this.mValue, 0, offset, valueBytes.size)
            return true
        }
        return false
    }


    /**
     * Returns the size of a give value type.
     */
    private fun getTypeLen(formatType: Int): Int {
        return formatType and 0xF
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    private fun unsignedByteToInt(b: Byte): Int {
        return (b.toInt() and 0xFF)

    }

    /**
     * Convert signed bytes to a 16-bit unsigned int.
     */
    private fun unsignedBytesToInt(b0: Byte, b1: Byte): Int {
        return unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
    }

    /**
     * Convert signed bytes to a 32-bit unsigned int.
     */
    private fun unsignedBytesToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
                + (unsignedByteToInt(b2) shl 16) + (unsignedByteToInt(b3) shl 24))
    }
    private fun bytesToFloat(b0: Byte, b1: Byte): Float {
        val mantissa = unsignedToSigned(
            unsignedByteToInt(b0)
                    + (unsignedByteToInt(b1) and 0x0F shl 8), 12
        )
        val exponent = unsignedToSigned(unsignedByteToInt(b1) shr 4, 4)
        return (mantissa * 10.0.pow(exponent.toDouble())).toFloat()
    }
    private fun bytesToFloat(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Float {
        val mantissa = unsignedToSigned(
            unsignedByteToInt(b0)
                    + (unsignedByteToInt(b1) shl 8)
                    + (unsignedByteToInt(b2) shl 16), 24
        )
        return (mantissa * 10.0.pow(b3.toDouble())).toFloat()
    }
    private fun unsignedToSigned(unsigned: Int, size: Int): Int {
        var unsigned = unsigned
        if (unsigned and (1 shl size - 1) != 0) {
            unsigned = -1 * ((1 shl size - 1) - (unsigned and (1 shl size - 1) - 1))
        }
        return unsigned
    }
    private fun intToSignedBits(i: Int, size: Int): Int {
        var i = i
        if (i < 0) {
            i = (1 shl size - 1) + (i and (1 shl size - 1) - 1)
        }
        return i
    }
    private fun prepareArray(neededLength: Int) {
        if (neededLength > mValue.size) {
            val largerByteArray = ByteArray(neededLength)
            mValue.copyInto(largerByteArray, 0, 0, mValue.size)
            mValue = largerByteArray
        }
    }

    override fun toString(): String {
        return bytes2String(mValue)
    }

    companion object {
        const val FORMAT_UINT8 = 0x11
        const val FORMAT_UINT16 = 0x12
        const val FORMAT_UINT32 = 0x14
        const val FORMAT_SINT8 = 0x21
        const val FORMAT_SINT16 = 0x22
        const val FORMAT_SINT32 = 0x24
        const val FORMAT_SFLOAT = 0x32
        const val FORMAT_FLOAT = 0x34

        fun bytes2String(bytes: ByteArray): String {
            return bytes.decodeToString()
        }
        fun string2bytes(hexString: String?): ByteArray {
            if (hexString == null) return ByteArray(0)
            val result = ByteArray(hexString.length / 2)
            for (i in result.indices) {
                val index = i * 2
                result[i] = hexString.substring(index, index + 2).toInt(16).toByte()
            }
            return result
        }
        fun mergeArrays(vararg arrays: ByteArray): ByteArray {
            var size = 0
            for (array in arrays) {
                size += array.size
            }
            val merged = ByteArray(size)
            var index = 0
            for (array in arrays) {
                array.copyInto(merged, 0, index, array.size)
                index += array.size
            }
            return merged
        }
    }
    init {
        this.offset = offset
        this.byteOrder = byteOrder
    }
}