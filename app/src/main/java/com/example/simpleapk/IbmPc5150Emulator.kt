package com.example.simpleapk

import android.content.res.AssetManager
import java.io.FileNotFoundException
import java.util.ArrayDeque
import kotlin.math.min

class IbmPc5150Emulator {
    val video = CgaTextScreen()
    val keyboard = PcKeyboard()
    private val bus = PcBus(video, keyboard)
    private val cpu = Cpu8088(bus)
    var biosName: String = "built-in monitor"
        private set

    fun bootFromAssets(assets: AssetManager) {
        val rom = try {
            assets.open("roms/glabios.bin").use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            null
        }

        bus.reset()
        cpu.reset()
        if (rom != null && rom.isNotEmpty()) {
            bus.loadRom(rom)
            biosName = "GLaBIOS asset"
            cpu.cs = 0xF000
            cpu.ip = 0xFFF0
        } else {
            biosName = "placeholder; add app/src/main/assets/roms/glabios.bin"
            bus.loadDemoBoot()
            cpu.cs = 0xF000
            cpu.ip = 0x0100
        }
    }

    fun runFrame() {
        repeat(18_000) {
            if (!cpu.halted) cpu.step() else return@repeat
        }
        if (cpu.halted) bus.runMonitorTick()
    }
}

class PcBus(private val video: CgaTextScreen, private val keyboard: PcKeyboard) {
    private val ram = ByteArray(1024 * 1024)
    private var pit = 0

    fun reset() {
        ram.fill(0)
        video.clear()
        pit = 0
    }

    fun rb(addr: Int): Int = ram[addr and 0xFFFFF].toInt() and 0xFF
    fun wb(addr: Int, value: Int) {
        val a = addr and 0xFFFFF
        ram[a] = value.toByte()
        if (a in 0xB8000 until 0xB8000 + 4000 && a % 2 == 0) video.syncByte(a - 0xB8000, value)
    }
    fun rw(addr: Int): Int = rb(addr) or (rb(addr + 1) shl 8)
    fun ww(addr: Int, value: Int) { wb(addr, value); wb(addr + 1, value ushr 8) }
    fun portIn(port: Int): Int = when (port and 0xFFFF) {
        0x60 -> keyboard.popScanCode()
        0x61 -> 0x30
        0x40 -> pit++ and 0xFF
        else -> 0xFF
    }
    fun portOut(port: Int, value: Int) {
        if ((port and 0xFFFF) == 0xE9) video.putChar((value and 0xFF).toChar())
    }
    fun loadRom(rom: ByteArray) {
        val start = 0x100000 - min(rom.size, 0x10000)
        for (i in 0 until min(rom.size, 0x10000)) ram[start + i] = rom[rom.size - min(rom.size, 0x10000) + i]
    }
    fun loadDemoBoot() {
        val code = intArrayOf(
            0xB8,0x00,0xB8, 0x8E,0xD8, 0xBE,0x28,0x01, 0x31,0xFF, 0xB9,0xD0,0x00,
            0xFC, 0xAC, 0x08,0xC0, 0x74,0x08, 0xAA, 0xB0,0x0A, 0xAA, 0xE2,0xF5,
            0xF4
        )
        val msg = "GLaBIOS ROM not bundled. Add glabios.bin to assets/roms/.\r\n" +
            "IBM PC 5150 profile: 8088 4.77 MHz, 16 KB base RAM, CGA text.\r\n" +
            "Type here: "
        val base = 0xF0100
        code.forEachIndexed { i, b -> ram[base + i] = b.toByte() }
        msg.encodeToByteArray().forEachIndexed { i, b -> ram[0xF0128 + i] = b }
        ram[0xF0128 + msg.length] = 0
    }
    fun interrupt(cpu: Cpu8088, intNo: Int) {
        when (intNo) {
            0x10 -> when ((cpu.ax ushr 8) and 0xFF) {
                0x0E -> video.putChar((cpu.ax and 0xFF).toChar())
                0x00 -> video.clear()
            }
            0x16 -> if ((cpu.ax ushr 8) and 0xFF == 0) cpu.ax = keyboard.popAscii().code
            0x19 -> { cpu.cs = 0xF000; cpu.ip = 0x0100 }
            0x20 -> cpu.halted = true
            else -> video.status("Unhandled INT ${intNo.toString(16)}")
        }
    }
    fun runMonitorTick() {
        keyboard.popAsciiOrNull()?.let { video.putChar(it) }
    }
}

class Cpu8088(private val bus: PcBus) {
    var ax = 0; var bx = 0; var cx = 0; var dx = 0; var sp = 0xFFFE; var bp = 0; var si = 0; var di = 0
    var cs = 0xF000; var ds = 0; var es = 0; var ss = 0; var ip = 0; var flags = 0x0200; var halted = false
    private var segOverride: Int? = null

    fun reset() { ax=0; bx=0; cx=0; dx=0; sp=0xFFFE; bp=0; si=0; di=0; ds=0; es=0; ss=0; flags=0x0200; halted=false; segOverride=null }
    private fun phys(seg: Int, off: Int) = (((seg and 0xFFFF) shl 4) + (off and 0xFFFF)) and 0xFFFFF
    private fun fetch8() = bus.rb(phys(cs, ip)).also { ip = (ip + 1) and 0xFFFF }
    private fun fetch16() = fetch8() or (fetch8() shl 8)
    private fun rb(seg: Int, off: Int) = bus.rb(phys(seg, off)); private fun wb(seg: Int, off: Int, v: Int) = bus.wb(phys(seg, off), v)
    private fun rw(seg: Int, off: Int) = bus.rw(phys(seg, off)); private fun ww(seg: Int, off: Int, v: Int) = bus.ww(phys(seg, off), v)
    private fun push(v: Int) { sp = (sp - 2) and 0xFFFF; ww(ss, sp, v) }
    private fun pop(): Int = rw(ss, sp).also { sp = (sp + 2) and 0xFFFF }

    fun step() {
        var op = fetch8()
        segOverride = null
        while (op in intArrayOf(0x26, 0x2E, 0x36, 0x3E)) {
            segOverride = when (op) { 0x26 -> es; 0x2E -> cs; 0x36 -> ss; else -> ds }
            op = fetch8()
        }
        when (op) {
            0x90 -> {}
            0xF4 -> halted = true
            0xFA, 0xFB, 0xFC, 0xFD -> {}
            in 0xB8..0xBF -> setReg16(op - 0xB8, fetch16())
            in 0xB0..0xB7 -> setReg8(op - 0xB0, fetch8())
            0x8E -> { val m = modrm(); setSeg(m.reg, m.read16()) }
            0x8C -> { val m = modrm(); m.write16(getSeg(m.reg)) }
            0x89 -> { val m = modrm(); m.write16(getReg16(m.reg)) }
            0x8B -> { val m = modrm(); setReg16(m.reg, m.read16()) }
            0x88 -> { val m = modrm(); m.write8(getReg8(m.reg)) }
            0x8A -> { val m = modrm(); setReg8(m.reg, m.read8()) }
            0xA0 -> setReg8(0, rb(segOverride ?: ds, fetch16()))
            0xA1 -> ax = rw(segOverride ?: ds, fetch16())
            0xA2 -> wb(segOverride ?: ds, fetch16(), ax)
            0xA3 -> ww(segOverride ?: ds, fetch16(), ax)
            0xAC -> { setReg8(0, rb(ds, si)); si = (si + 1) and 0xFFFF }
            0xAD -> { ax = rw(ds, si); si = (si + 2) and 0xFFFF }
            0xAA -> { wb(es, di, ax); di = (di + 1) and 0xFFFF }
            0xAB -> { ww(es, di, ax); di = (di + 2) and 0xFFFF }
            0x31 -> { val m = modrm(); val r = getReg16(m.reg); m.write16(xor16(m.read16(), r)) }
            0x33 -> { val m = modrm(); setReg16(m.reg, xor16(getReg16(m.reg), m.read16())) }
            0x08 -> { val m = modrm(); m.write8(or8(m.read8(), getReg8(m.reg))) }
            0x09 -> { val m = modrm(); m.write16(or16(m.read16(), getReg16(m.reg))) }
            0x04 -> setReg8(0, add8(getReg8(0), fetch8()))
            0x05 -> ax = add16(ax, fetch16())
            0x2C -> setReg8(0, sub8(getReg8(0), fetch8()))
            0x2D -> ax = sub16(ax, fetch16())
            0x3C -> sub8(getReg8(0), fetch8())
            0x3D -> sub16(ax, fetch16())
            0x74 -> if (zf()) ip = (ip + fetch8().toByte()) and 0xFFFF else fetch8()
            0x75 -> if (!zf()) ip = (ip + fetch8().toByte()) and 0xFFFF else fetch8()
            0xEB -> ip = (ip + fetch8().toByte()) and 0xFFFF
            0xE9 -> ip = (ip + fetch16().toShort()) and 0xFFFF
            0xE8 -> { val d = fetch16().toShort(); push(ip); ip = (ip + d) and 0xFFFF }
            0xC3 -> ip = pop()
            0xCD -> bus.interrupt(this, fetch8())
            0xE4 -> setReg8(0, bus.portIn(fetch8()))
            0xE5 -> ax = bus.portIn(fetch8())
            0xE6 -> bus.portOut(fetch8(), ax)
            0xE7 -> bus.portOut(fetch8(), ax)
            0xE2 -> { val d = fetch8().toByte(); cx = (cx - 1) and 0xFFFF; if (cx != 0) ip = (ip + d) and 0xFFFF }
            else -> { bus.interrupt(this, 0x20); halted = true }
        }
    }

    private fun modrm(): EA {
        val b = fetch8(); val mod = b ushr 6; val reg = (b ushr 3) and 7; val rm = b and 7
        if (mod == 3) return EA(reg, null, 0, rm)
        var disp = when (mod) { 1 -> fetch8().toByte().toInt(); 2 -> fetch16(); else -> 0 }
        if (mod == 0 && rm == 6) disp = fetch16()
        val off = (when (rm) { 0 -> bx + si; 1 -> bx + di; 2 -> bp + si; 3 -> bp + di; 4 -> si; 5 -> di; 6 -> if (mod == 0) 0 else bp; else -> bx } + disp) and 0xFFFF
        val seg = segOverride ?: if (rm in 2..3 || (rm == 6 && mod != 0)) ss else ds
        return EA(reg, seg, off, rm)
    }
    inner class EA(val reg: Int, val seg: Int?, val off: Int, private val rm: Int) {
        fun read8() = if (seg == null) getReg8(rm) else rb(seg, off)
        fun read16() = if (seg == null) getReg16(rm) else rw(seg, off)
        fun write8(v: Int) { if (seg == null) setReg8(rm, v) else wb(seg, off, v) }
        fun write16(v: Int) { if (seg == null) setReg16(rm, v) else ww(seg, off, v) }
    }
    private fun getReg16(i: Int) = intArrayOf(ax, cx, dx, bx, sp, bp, si, di)[i and 7]
    private fun setReg16(i: Int, v: Int) { when (i and 7) { 0 -> ax=v; 1 -> cx=v; 2 -> dx=v; 3 -> bx=v; 4 -> sp=v; 5 -> bp=v; 6 -> si=v; 7 -> di=v } }
    private fun getReg8(i: Int) = when (i and 7) { 0 -> ax; 1 -> cx; 2 -> dx; 3 -> bx; 4 -> ax ushr 8; 5 -> cx ushr 8; 6 -> dx ushr 8; else -> bx ushr 8 } and 0xFF
    private fun setReg8(i: Int, v: Int) { val x = v and 0xFF; when (i and 7) { 0 -> ax=(ax and 0xFF00) or x; 1 -> cx=(cx and 0xFF00) or x; 2 -> dx=(dx and 0xFF00) or x; 3 -> bx=(bx and 0xFF00) or x; 4 -> ax=(ax and 0x00FF) or (x shl 8); 5 -> cx=(cx and 0x00FF) or (x shl 8); 6 -> dx=(dx and 0x00FF) or (x shl 8); 7 -> bx=(bx and 0x00FF) or (x shl 8) } }
    private fun getSeg(i: Int) = intArrayOf(es, cs, ss, ds)[i and 3]
    private fun setSeg(i: Int, v: Int) { when (i and 3) { 0 -> es=v; 1 -> cs=v; 2 -> ss=v; 3 -> ds=v } }
    private fun setSz(v: Int, bits: Int) { val mask = if (bits == 8) 0xFF else 0xFFFF; flags = if ((v and mask) == 0) flags or 0x40 else flags and 0x40.inv(); flags = if ((v and (1 shl (bits - 1))) != 0) flags or 0x80 else flags and 0x80.inv() }
    private fun zf() = flags and 0x40 != 0
    private fun add8(a:Int,b:Int)=((a+b).also{setSz(it,8)}) and 0xFF; private fun add16(a:Int,b:Int)=((a+b).also{setSz(it,16)}) and 0xFFFF
    private fun sub8(a:Int,b:Int)=((a-b).also{setSz(it,8)}) and 0xFF; private fun sub16(a:Int,b:Int)=((a-b).also{setSz(it,16)}) and 0xFFFF
    private fun xor16(a:Int,b:Int)=((a xor b).also{setSz(it,16)}) and 0xFFFF; private fun or8(a:Int,b:Int)=((a or b).also{setSz(it,8)}) and 0xFF; private fun or16(a:Int,b:Int)=((a or b).also{setSz(it,16)}) and 0xFFFF
}

class CgaTextScreen {
    private val chars = CharArray(80 * 25) { ' ' }
    private var cursor = 0
    fun clear() { chars.fill(' '); cursor = 0 }
    fun syncByte(offset: Int, value: Int) { val cell = offset / 2; if (cell in chars.indices) chars[cell] = printable(value) }
    fun putChar(ch: Char) {
        when (ch) {
            '\r' -> cursor -= cursor % 80
            '\n' -> cursor += 80 - cursor % 80
            '\b' -> if (cursor > 0) chars[--cursor] = ' '
            else -> { if (cursor in chars.indices) chars[cursor++] = printable(ch.code); if (cursor >= chars.size) scroll() }
        }
    }
    fun status(text: String) { text.take(80).forEachIndexed { i, c -> chars[24 * 80 + i] = c } }
    fun snapshot(): List<String> = List(25) { row -> String(chars, row * 80, 80) }
    private fun scroll() { for (i in 0 until 24 * 80) chars[i] = chars[i + 80]; for (i in 24 * 80 until 25 * 80) chars[i] = ' '; cursor = 24 * 80 }
    private fun printable(v: Int) = if (v in 32..126) v.toChar() else ' '
}

class PcKeyboard {
    private val ascii = ArrayDeque<Char>()
    fun push(c: Char) { ascii.add(c) }
    fun popAscii(): Char = while (ascii.isEmpty()) Thread.sleep(1).let { } .let { ascii.removeFirst() }
    fun popAsciiOrNull(): Char? = if (ascii.isEmpty()) null else ascii.removeFirst()
    fun popScanCode(): Int = popAsciiOrNull()?.code ?: 0
}
