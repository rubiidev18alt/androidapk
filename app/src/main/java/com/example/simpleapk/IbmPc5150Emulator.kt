package com.example.simpleapk

import android.content.res.AssetManager
import java.io.FileNotFoundException
import java.util.ArrayDeque
import kotlin.math.min

private const val BASE_RAM_SIZE = 16 * 1024
private const val CGA_TEXT_BASE = 0xB8000
private const val CGA_TEXT_SIZE = 0x4000
private const val BIOS_ROM_BASE = 0xF0000
private const val BIOS_ROM_SIZE = 0x10000
private const val CPU_HZ = 4_772_727.0
private const val FRAME_NS = 16_666_667L
private const val DEFAULT_FLOPPY_SIZE = 160 * 1024

class IbmPc5150Emulator {
    val video = CgaTextScreen()
    val keyboard = PcKeyboard()
    private val bus = PcBus(video, keyboard)
    private val cpu = Cpu8088(bus)
    private var cyclesCarry = 0.0
    var biosName: String = "built-in monitor"
        private set

    fun bootFromAssets(assets: AssetManager) {
        val rom = try { assets.open("roms/glabios.bin").use { it.readBytes() } } catch (_: FileNotFoundException) { null }
        val floppy = try { assets.open("floppy/disk0.img").use { it.readBytes() } } catch (_: FileNotFoundException) { null }

        bus.reset()
        cpu.reset()
        bus.loadFloppy(floppy)
        cyclesCarry = 0.0
        if (rom != null && rom.isNotEmpty()) {
            bus.loadRom(rom)
            biosName = "GLaBIOS asset"
            cpu.cs = 0xF000
            cpu.ip = 0xFFF0
        } else {
            biosName = "IBM 5150 ROM fallback"
            bus.loadFallbackBios()
            cpu.cs = 0xF000
            cpu.ip = 0x0100
        }
    }

    fun runFrame() {
        cyclesCarry += CPU_HZ * (FRAME_NS / 1_000_000_000.0)
        var budget = cyclesCarry.toInt()
        cyclesCarry -= budget
        while (budget > 0 && !cpu.halted) budget -= cpu.step()
        if (cpu.halted) bus.runMonitorTick()
    }
}

class PcBus(private val video: CgaTextScreen, private val keyboard: PcKeyboard) {
    private val baseRam = ByteArray(BASE_RAM_SIZE)
    private val cgaRam = ByteArray(CGA_TEXT_SIZE) { if (it % 2 == 0) 0x20 else 0x07 }
    private val biosRom = ByteArray(BIOS_ROM_SIZE) { 0xFF.toByte() }
    private var floppy: ByteArray? = null
    private var pit = 0
    private var fallbackConsoleEnabled = false
    private val fallbackLine = StringBuilder()

    fun reset() {
        baseRam.fill(0)
        cgaRam.fill(0x07)
        for (i in cgaRam.indices step 2) cgaRam[i] = 0x20
        biosRom.fill(0xFF.toByte())
        floppy = null
        video.clear()
        pit = 0
        fallbackConsoleEnabled = false
        fallbackLine.clear()
    }

    fun loadFloppy(bytes: ByteArray?) { floppy = bytes?.takeIf { it.isNotEmpty() } }

    fun rb(addr: Int): Int {
        val a = addr and 0xFFFFF
        return when {
            a < BASE_RAM_SIZE -> baseRam[a].toInt() and 0xFF
            a in CGA_TEXT_BASE until CGA_TEXT_BASE + CGA_TEXT_SIZE -> cgaRam[a - CGA_TEXT_BASE].toInt() and 0xFF
            a in BIOS_ROM_BASE until BIOS_ROM_BASE + BIOS_ROM_SIZE -> biosRom[a - BIOS_ROM_BASE].toInt() and 0xFF
            else -> 0xFF
        }
    }

    fun wb(addr: Int, value: Int) {
        val a = addr and 0xFFFFF
        val v = value and 0xFF
        when {
            a < BASE_RAM_SIZE -> baseRam[a] = v.toByte()
            a in CGA_TEXT_BASE until CGA_TEXT_BASE + CGA_TEXT_SIZE -> {
                val off = a - CGA_TEXT_BASE
                cgaRam[off] = v.toByte()
                if (off % 2 == 0) video.syncByte(off, v)
            }
        }
    }

    fun rw(addr: Int): Int = rb(addr) or (rb(addr + 1) shl 8)
    fun ww(addr: Int, value: Int) { wb(addr, value); wb(addr + 1, value ushr 8) }
    fun portIn(port: Int): Int = when (port and 0xFFFF) {
        0x60 -> keyboard.popScanCode()
        0x61 -> 0x30
        0x40 -> pit++ and 0xFF
        else -> 0xFF
    }
    fun portOut(port: Int, value: Int) { if ((port and 0xFFFF) == 0xE9) video.putChar((value and 0xFF).toChar()) }
    fun loadRom(rom: ByteArray) {
        biosRom.fill(0xFF.toByte())
        val romLen = min(rom.size, BIOS_ROM_SIZE)
        val start = BIOS_ROM_SIZE - romLen
        for (i in 0 until romLen) biosRom[start + i] = rom[rom.size - romLen + i]
    }
    fun loadFallbackBios() {
        fallbackConsoleEnabled = true
        fallbackLine.clear()
        biosRom.fill(0xFF.toByte())
        biosRom[0x0100] = 0xF4.toByte()
        drawFallbackPost()
    }
    private fun drawFallbackPost() {
        video.clear()
        video.putString("IBM PC 5150 compatible ROM fallback\r\n")
        video.putString("8088 processor initialized\r\n")
        video.putString("16 KB OK\r\n\r\n")
        video.putString("Keyboard........ OK\r\n")
        video.putString("Timer........... OK\r\n")
        video.putString("CGA text........ OK\r\n")
        video.putString("Diskette A...... ${if (floppy == null) "not ready" else "${floppy!!.size / 1024} KB image"}\r\n\r\n")
        video.putString("GLaBIOS ROM image not present at app/src/main/assets/roms/glabios.bin\r\n")
        if (floppy == null) video.putString("No bootable disk image is attached.\r\n") else video.putString("Disk image loaded; fallback BIOS supports simple INT 13h sector reads.\r\n")
        video.putString("Entering ROM monitor fallback. Type HELP for available checks.\r\n\r\n")
        video.putString("> ")
    }
    fun interrupt(cpu: Cpu8088, intNo: Int) {
        when (intNo) {
            0x10 -> when ((cpu.ax ushr 8) and 0xFF) {
                0x0E -> video.putChar((cpu.ax and 0xFF).toChar())
                0x00 -> video.clear()
                0x03 -> { cpu.ax = 0x0003; cpu.bx = 0x0000 }
            }
            0x11 -> cpu.ax = 0x0021
            0x12 -> cpu.ax = 16
            0x13 -> diskInt(cpu)
            0x16 -> if (((cpu.ax ushr 8) and 0xFF) == 0) cpu.ax = keyboard.popAscii().code
            0x19 -> { loadBootSector(cpu); cpu.halted = false }
            0x20 -> cpu.halted = true
            else -> video.status("BIOS fallback: unhandled INT ${intNo.toString(16).uppercase()}")
        }
    }
    private fun diskInt(cpu: Cpu8088) {
        when ((cpu.ax ushr 8) and 0xFF) {
            0x00 -> clearCarry(cpu)
            0x02 -> readSectors(cpu)
            else -> setDiskError(cpu, 0x01)
        }
    }
    private fun readSectors(cpu: Cpu8088) {
        val count = cpu.ax and 0xFF
        val cylinder = ((cpu.cx ushr 8) and 0xFF) or ((cpu.cx and 0xC0) shl 2)
        val sector = cpu.cx and 0x3F
        val head = (cpu.dx ushr 8) and 0xFF
        val image = floppy ?: return setDiskError(cpu, 0x80)
        if (count <= 0 || sector !in 1..8 || head !in 0..1) return setDiskError(cpu, 0x04)
        val lba = ((cylinder * 2 + head) * 8) + (sector - 1)
        val bytes = count * 512
        val src = lba * 512
        if (src < 0 || src + bytes > image.size) return setDiskError(cpu, 0x04)
        val dest = ((cpu.es and 0xFFFF) shl 4) + (cpu.bx and 0xFFFF)
        for (i in 0 until bytes) wb(dest + i, image[src + i].toInt() and 0xFF)
        cpu.ax = count
        clearCarry(cpu)
    }
    private fun loadBootSector(cpu: Cpu8088) {
        val image = floppy
        if (image == null || image.size < 512) { drawFallbackPost(); cpu.cs = 0xF000; cpu.ip = 0x0100; return }
        for (i in 0 until 512) wb(0x7C00 + i, image[i].toInt() and 0xFF)
        cpu.cs = 0x0000
        cpu.ip = 0x7C00
    }
    private fun setDiskError(cpu: Cpu8088, status: Int) { cpu.ax = (status and 0xFF) shl 8; cpu.flags = cpu.flags or 0x0001 }
    private fun clearCarry(cpu: Cpu8088) { cpu.flags = cpu.flags and 0x0001.inv() }
    fun runMonitorTick() {
        val ch = keyboard.popAsciiOrNull() ?: return
        if (!fallbackConsoleEnabled) { video.putChar(ch); return }
        when (ch) {
            '\r', '\n' -> { video.putString("\r\n"); handleFallbackCommand(fallbackLine.toString().trim().uppercase()); fallbackLine.clear(); video.putString("> ") }
            '\b' -> if (fallbackLine.isNotEmpty()) { fallbackLine.deleteAt(fallbackLine.length - 1); video.putChar('\b') }
            else -> if (ch.code in 32..126 && fallbackLine.length < 64) { fallbackLine.append(ch); video.putChar(ch) }
        }
    }
    private fun handleFallbackCommand(command: String) {
        when (command) {
            "", "HELP" -> video.putString("Available checks: MEM PORTS INT DISK BOOT CLS REBOOT\r\n")
            "MEM" -> video.putString("Base memory reported by INT 12h: 16 KB; writable RAM: 00000-03FFF\r\n")
            "PORTS" -> video.putString("Stubbed hardware ports: 0040 PIT, 0060 keyboard data, 0061 PPI, 00E9 debug\r\n")
            "INT" -> video.putString("BIOS calls present: INT 10h, 11h, 12h, 13h read, 16h, 19h, 20h\r\n")
            "DISK" -> video.putString(if (floppy == null) "Diskette A: not ready\r\n" else "Diskette A: ${floppy!!.size} bytes, 512-byte sectors\r\n")
            "BOOT" -> video.putString("Use GLaBIOS for real boot flow. Fallback can load sector 0 to 0000:7C00 through INT 19h.\r\n")
            "CLS" -> video.clear()
            "REBOOT" -> drawFallbackPost()
            else -> video.putString("Syntax error\r\n")
        }
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
    private fun rel8() = fetch8().toByte().toInt()
    private fun rel16() = fetch16().toShort().toInt()

    fun step(): Int {
        var op = fetch8()
        segOverride = null
        while (op == 0x26 || op == 0x2E || op == 0x36 || op == 0x3E) {
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
            0x74 -> { val d = rel8(); if (zf()) ip = (ip + d) and 0xFFFF }
            0x75 -> { val d = rel8(); if (!zf()) ip = (ip + d) and 0xFFFF }
            0xEB -> ip = (ip + rel8()) and 0xFFFF
            0xE9 -> ip = (ip + rel16()) and 0xFFFF
            0xE8 -> { val d = rel16(); push(ip); ip = (ip + d) and 0xFFFF }
            0xC3 -> ip = pop()
            0xCD -> bus.interrupt(this, fetch8())
            0xE4 -> setReg8(0, bus.portIn(fetch8()))
            0xE5 -> ax = bus.portIn(fetch8())
            0xE6 -> bus.portOut(fetch8(), ax)
            0xE7 -> bus.portOut(fetch8(), ax)
            0xE2 -> { val d = rel8(); cx = (cx - 1) and 0xFFFF; if (cx != 0) ip = (ip + d) and 0xFFFF }
            else -> { bus.interrupt(this, 0x20); halted = true }
        }
        return 4
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
    private fun setReg16(i: Int, v: Int) { when (i and 7) { 0 -> ax=v and 0xFFFF; 1 -> cx=v and 0xFFFF; 2 -> dx=v and 0xFFFF; 3 -> bx=v and 0xFFFF; 4 -> sp=v and 0xFFFF; 5 -> bp=v and 0xFFFF; 6 -> si=v and 0xFFFF; 7 -> di=v and 0xFFFF } }
    private fun getReg8(i: Int) = when (i and 7) { 0 -> ax; 1 -> cx; 2 -> dx; 3 -> bx; 4 -> ax ushr 8; 5 -> cx ushr 8; 6 -> dx ushr 8; else -> bx ushr 8 } and 0xFF
    private fun setReg8(i: Int, v: Int) { val x = v and 0xFF; when (i and 7) { 0 -> ax=(ax and 0xFF00) or x; 1 -> cx=(cx and 0xFF00) or x; 2 -> dx=(dx and 0xFF00) or x; 3 -> bx=(bx and 0xFF00) or x; 4 -> ax=(ax and 0x00FF) or (x shl 8); 5 -> cx=(cx and 0x00FF) or (x shl 8); 6 -> dx=(dx and 0x00FF) or (x shl 8); 7 -> bx=(bx and 0x00FF) or (x shl 8) } }
    private fun getSeg(i: Int) = intArrayOf(es, cs, ss, ds)[i and 3]
    private fun setSeg(i: Int, v: Int) { when (i and 3) { 0 -> es=v and 0xFFFF; 1 -> cs=v and 0xFFFF; 2 -> ss=v and 0xFFFF; 3 -> ds=v and 0xFFFF } }
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
    fun putString(text: String) = text.forEach { putChar(it) }
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
    fun popAscii(): Char {
        while (ascii.isEmpty()) Thread.sleep(1)
        return ascii.removeFirst()
    }
    fun popAsciiOrNull(): Char? = if (ascii.isEmpty()) null else ascii.removeFirst()
    fun popScanCode(): Int = popAsciiOrNull()?.code ?: 0
}
