package venus
/* ktlint-disable no-wildcard-imports */

import org.w3c.dom.*
import venus.vfs.*
import venusbackend.assembler.AssemblerError
import venusbackend.linker.LinkedProgram
import venusbackend.riscv.*
import venusbackend.riscv.insts.dsl.types.Instruction
import venusbackend.riscv.insts.floating.Decimal
import venusbackend.simulator.Diff
import venusbackend.simulator.Simulator
import venusbackend.simulator.SimulatorError
import venusbackend.simulator.cache.BlockState
import venusbackend.simulator.cache.ChangedBlockState
import venusbackend.simulator.diffs.*
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.removeClass

/* ktlint-enable no-wildcard-imports */

/**
 * This singleton is used to render different parts of the screen, it serves as an interface between the UI and the
 * internal simulator.
 *
 * @todo break this up into multiple objects
 */
internal object Renderer {
    /** The register currently being highlighted */
    private var activeRegister: HTMLElement? = null
    /** The instruction currently being highlighted */
    private var activeInstruction: HTMLElement? = null
    /** The memory location currently centered */
    var activeMemoryAddress: Int = 0
    /** The simulator being rendered */
    private var sim: Simulator = Simulator(LinkedProgram(), VirtualFileSystem("dummy"))
    /* The way the information in the registers is displayed*/
    var displayType = "hex"

    var mainTabs: ArrayList<String> = arrayListOf("simulator", "editor", "venus")
    val simulatorTabs: ArrayList<String> = arrayListOf("register", "memory", "cache", "vdb")
    val venusMainSettingsTabs: ArrayList<String> = arrayListOf("settings")
    val venusSubSettingsTabs: ArrayList<String> = arrayListOf("general-settings", "tracer-settings", "calling-convention-settings", "packages")
    val simulatorRegisterTabs: ArrayList<String> = arrayListOf("regs", "fregs")
    val venusSimulatorVDBTabs: ArrayList<String> = arrayListOf("breakpoints", "watchpoints")

    @JsName("renderTab") fun renderTab(tab: String, tabs: List<String>) {
        if (!tabs.contains(tab)) {
            return
        }
        for (t in tabs) {
            var disp = "none"
            if (t == tab) {
                disp = "block"
            }
            tabSetVisibility(t, disp)
        }
    }

    @JsName("addTab") fun addTab(tabName: String, tabList: ArrayList<String>): Boolean {
        if (!tabList.contains(tabName)) {
            tabList.add(tabName)
            return true
        }
        return false
    }

    @JsName("removeTab") fun removeTab(tabName: String, tabList: ArrayList<String>): Boolean {
        if (tabList.contains(tabName)) {
            tabList.remove(tabName)
            return true
        }
        return false
    }

    /**
     * Shows the simulator tab and hides other tabs
     *
     * @param displaySim the simulator to show
     */
    fun renderSimulator() {
        renderTab("simulator", mainTabs)
    }

    fun loadSimulator(displaySim: Simulator) {
        sim = displaySim
        setRunButtonSpinning(false)
        renderProgramListing()
        clearConsole()
        updateAll()
        renderSimButtons()
    }

    fun renderSimButtons() {
        // Turn all simulator-related buttons on, except for "Assemble & Simulate..."
        val simbtns = document.getElementById("simulator-buttons") as HTMLDivElement
        simbtns.style.display = ""
        val assembleContainer = document.getElementById("simulator-assemble-simulator-container") as HTMLDivElement
        assembleContainer.style.display = "none"
    }

    fun renderAssembleButtons() {
        // Turn all simulator-related buttons off, except for "Assemble & Simulate..."
        val simbtns = document.getElementById("simulator-buttons") as HTMLDivElement
        simbtns.style.display = "none"
        val assembleContainer = document.getElementById("simulator-assemble-simulator-container") as HTMLDivElement
        assembleContainer.style.display = ""
    }

    /** Shows the editor tab and hides other tabs */
    fun renderEditor() {
        renderTab("editor", mainTabs)
        renderAssembleButtons()
    }

    fun renderVenus() {
        renderTab("venus", mainTabs)
    }

    fun renderURLMaker() {
        renderTab("urlmaker", mainTabs)
    }

    /**
     * Sets the tab to the desired visiblity.
     *
     * Also updates the highlighted tab at the top.
     *
     * @param tab the name of the tab (currently "editor" or "simulator")
     */
    @JsName("tabSetVisibility") private fun tabSetVisibility(tab: String, display: String) {
        val tabView = document.getElementById("$tab-tab-view") as HTMLElement
        val tabDisplay = document.getElementById("$tab-tab") as HTMLElement
        tabView.style.display = display
        if (display == "none") {
            tabDisplay.classList.remove("is-active")
        } else {
            tabDisplay.classList.add("is-active")
        }
    }

    fun displayWarning(w: String) {
        printConsole(w)
    }

    /** Display a given ERROR */
    fun displayError(thing: Any) {
//        printConsole("\n------STDERR------\n")
        printConsole(thing)
//        printConsole("\n----STDERR_END----\n")
    }

    fun stdout(thing: Any) {
        printConsole(thing)
    }

    fun stderr(thing: Any) {
        displayError(thing)
    }

    /** Display a given [AssemblerError] */
    @Suppress("UNUSED_PARAMETER") fun displayAssemblerError(e: AssemblerError) {
//        if (e.line !== null) {
//            js("alert('[ERROR]\\n(Line: ' + e.line + ') ' + e.message)")
//        } else {
            js("alert('[ERROR]\\n' + e.message)")
//        }
    }

    /**
     * Renders the program listing under the debugger
     */
    private fun renderProgramListing() {
        clearProgramListing()
        for (i in 0 until sim.linkedProgram.prog.insts.size) {
            val programDebug = sim.linkedProgram.dbg[i]
            val (_, dbg) = programDebug
            val (_, line) = dbg
            val mcode = sim.linkedProgram.prog.insts[i]
            val pc = sim.instOrderMapping[i]!!
            addToProgramListing(pc, mcode, line)
        }
    }

    /**
     * Refresh all of the simulator tab's content
     *
     * @todo refactor this into a "reset" and "update" all function
     */
    fun updateAll() {
        updateText()
        updatePC(sim.getPC())
        updateMemory(activeMemoryAddress)
        updateControlButtons()
        for (i in 0..31) {
            updateRegister(i, sim.getReg(i))
        }
        for (i in 0..31) {
            updateFRegister(i, sim.getFReg(i))
        }
    }

    /**
     * Updates the view by applying each individual diff.
     *
     * @param diffs the list of diffs to apply
     */
    fun updateFromDiffs(diffs: List<Diff>) {
        for (diff in diffs) {
            when (diff) {
                is RegisterDiff -> updateRegister(diff.id, diff.v, true)
                is FRegisterDiff -> updateFRegister(diff.id, diff.v, true)
                is PCDiff -> updatePC(diff.pc)
                is MemoryDiff -> updateMemory(diff.addr.toInt())
                is CacheDiff -> updateCache(diff.addr)
                is InstructionDiff -> {}
                else -> {
                    println("diff not yet implemented")
                }
            }
        }
    }

    /**
     * Clears the current program listing.
     *
     * @todo find a less hacky way to do this?
     */
    fun clearProgramListing() {
        getElement("program-listing-body").innerHTML = ""
    }

    /**
     * Adds an instruction with the given index to the program listing.
     *
     * @param idx the pc of the instruction
     * @param mcode the machine code representation of the instruction
     * @param progLine the original assembly code
     */
    fun addToProgramListing(pcx: Int, mcode: MachineCode, progLine: String, invalidInst: Boolean = false) {
        val programTable = getElement("program-listing-body") as HTMLTableSectionElement

        val newRow = programTable.insertRow() as HTMLTableRowElement
        newRow.id = "instruction-$pcx"
        newRow.onclick = { Driver.addBreakpoint(pcx) }

        var cellIndex = 0
        val breakpointLine = newRow.insertCell(cellIndex)
        val breakpointNode = document.createElement("div")
        breakpointNode.setAttribute("name", "breakpoint_cell")
        breakpointLine.appendChild(breakpointNode)
        cellIndex++

        val pcline = newRow.insertCell(cellIndex)
        val pcText = document.createTextNode("0x" + (pcx).toString(16))
        pcline.appendChild(pcText)
        cellIndex++

        val hexRepresention = toHex(mcode[InstructionField.ENTIRE].toInt(), mcode.length * 2)
        val machineCode = newRow.insertCell(cellIndex)
        val machineCodeText = document.createTextNode(hexRepresention)
        machineCode.appendChild(machineCodeText)
        cellIndex++

        val basicCode = newRow.insertCell(cellIndex)
        val basicCodeText = document.createTextNode(if (invalidInst) progLine else Instruction[mcode].disasm(mcode))
        basicCode.appendChild(basicCodeText)
        cellIndex++

        val line = newRow.insertCell(cellIndex)
        val lineText = document.createTextNode(progLine)
        line.appendChild(lineText)
        cellIndex++
    }

    fun updateProgramListing(idx: Number, inst: Int, orig: String? = null): InstructionDiff {
        val instTab = document.getElementById("instruction-$idx")
        val children = instTab?.children
        val mcode = MachineCode(inst)
        var code = "Invalid Instruction"
        try {
            code = Instruction[mcode].disasm(mcode)
        } catch (e: SimulatorError) {}
        val pre = InstructionDiff(idx.toInt(), userStringToInt(children?.get(1)?.innerHTML ?: "-1"), children?.get(3)?.innerHTML ?: "")
        children?.get(1)?.innerHTML = toHex(mcode[InstructionField.ENTIRE].toInt()) /*Machine Code*/
        children?.get(2)?.innerHTML = code /*Basic Code*/
        children?.get(3)?.innerHTML = orig ?: code /*Original Code*/
        return pre
    }

    /**
     * Gets the element with a given ID
     *
     * @param id the id of the desired element
     *
     * @returns the HTML element corresponding to the given ID
     * @throws ClassCastException if the element is not an [HTMLElement] or does not exist
     */
    fun getElement(id: String): HTMLElement = document.getElementById(id) as HTMLElement

    /**
     * Updates the register with the given id and value.
     *
     * @param id the ID of the register (e.g., x13 has ID 13)
     * @param value the new value of the register
     * @param setActive whether the register should be set to the active register (i.e., highlighted for the user)
     */
    fun updateRegister(id: Int, value: Number, setActive: Boolean = false) {
        val register = getElement("reg-$id-val") as HTMLInputElement
        register.value = when (displayType) {
            "Hex" -> toHex(value.toInt())
            "Decimal" -> value.toString()
            "Unsigned" -> toUnsigned(value.toInt())
            "ASCII" -> toAscii(value.toInt())
            else -> toHex(value.toInt())
        }
        if (setActive) {
            activeRegister?.classList?.remove("is-modified")
            register.classList.add("is-modified")
            activeRegister = register
        }
    }
    /**
     * Updates the register with the given id and value.
     *
     * @param id the ID of the floating register (e.g., f13 has ID 13)
     * @param value the new value of the register
     * @param setActive whether the register should be set to the active register (i.e., highlighted for the user)
     */
    fun updateFRegister(id: Int, v: Decimal, setActive: Boolean = false) {
        val fregister = getElement("freg-$id-val") as HTMLInputElement
        fregister.value = when (displayType) {
            "Hex" -> v.toHex()
            "Decimal" -> v.toDecimal()
            "Unsigned" -> v.toUnsigned()
            "ASCII" -> v.toAscii()
            else -> v.toHex()
        }
        if (setActive) {
            activeRegister?.classList?.remove("is-modified")
            fregister.classList.add("is-modified")
            activeRegister = fregister
        }
    }

    fun intToString(value: Int): String {
        var v = when (displayType) {
            "Hex" -> toHex(value)
            "Decimal" -> value.toString()
            "Unsigned" -> toUnsigned(value)
            "ASCII" -> toAscii(value)
            else -> toHex(value)
        }
        return v
    }
    /*@TODO make it so I can detect between if I am continuing or not so I do not have to be too wasteful.*/
    fun updateCache(a: Address) {
        // println("Need to implement the update cHandler feature!")
        (document.getElementById("hit-count") as HTMLInputElement).value = Driver.cache.getHitCount().toString()
        val hr = Driver.cache.getHitRate()
        (document.getElementById("hit-rate") as HTMLInputElement).value = (if (hr.isNaN()) "???" else hr).toString()
        (document.getElementById("access-amt") as HTMLInputElement).value = Driver.cache.memoryAccessCount().toString()
        // (document.getElementById("cacheDebug") as HTMLDivElement).innerHTML = Driver.cache.getBlocksState().toString()
        try {
            updateAllCacheBlocks()
        } catch (e: Throwable) {
            makeCacheBlocks()
            updateAllCacheBlocks()
        }
    }

    fun renderSetCacheLevel(i: Int) {
        val clvl = document.getElementById("cacheLevel") as HTMLSelectElement
        clvl.value = "L" + i.toString()
    }

    fun renderAddCacheLevel() {
        val clvl = document.getElementById("cacheLevel") as HTMLSelectElement
        val newCacheNumber = clvl.options.length + 1
        val option = document.createElement("option") as HTMLOptionElement
        option.innerHTML = "L" + newCacheNumber.toString()
        clvl.options[clvl.options.length] = option
    }

    fun renderRemoveCacheLevel() {
        val clvl = document.getElementById("cacheLevel") as HTMLSelectElement
        clvl.options[clvl.options.length - 1] = null
    }

    fun makeCacheBlocks() {
        val t = document.createElement("table")
        t.setAttribute("style", "border-collapse: collapse;border: 1px solid black;width:100%;")
        val bs = Driver.cache.getBlocksState()
        val b = Driver.cache.currentState().getChangedBlockState()
        for (i in bs.indices) {
            val tr = document.createElement("tr")
            val th = document.createElement("th")
            if (!b.noChange && b.block == i) {
                tr.setAttribute("style", "border: 2px solid black;")
            } else {
                tr.setAttribute("style", "border: 1px solid black;")
            }
            th.id = "cache-block-" + i.toString()
            th.innerHTML = i.toString() + ") " + bs[i]
            tr.appendChild(th)
            t.appendChild(tr)
        }
        val cb = (document.getElementById("cacheBlocks") as HTMLDivElement)
        cb.innerHTML = ""
        cb.appendChild(t)
    }

    fun updateCacheBlocks(b: ChangedBlockState = Driver.cache.currentState().getChangedBlockState()) {
        if (!b.noChange) {
            val pb = Driver.cache.currentState().getPrevChangedBlock()
            if (pb != -1) {
                val prevelm = document.getElementById("cache-block-" + pb.toString())
                prevelm?.parentElement?.setAttribute("style", "border: 1px solid black;")
            }
            val elm = document.getElementById("cache-block-" + b.block.toString())
            elm?.parentElement?.setAttribute("style", "border: 2px solid black;")
            if (b.state == BlockState.HIT) {
                elm?.innerHTML = b.block.toString() + ") HIT"
                elm?.setAttribute("style", "background-color:#00d1b2;")
            } else if (b.state == BlockState.MISS) {
                elm?.innerHTML = b.block.toString() + ") MISS"
                elm?.setAttribute("style", "background-color:#ff4e4e;")
            } else {
                elm?.innerHTML = b.block.toString() + ") EMPTY"
                elm?.setAttribute("style", "")
            }
        }
    }

    fun updateAllCacheBlocks() {
        val bs = Driver.cache.currentState().getBlocksState()
        for (i in bs.indices) {
            val elm = document.getElementById("cache-block-" + i.toString())
            elm?.parentElement?.setAttribute("style", "border: 1px solid black;")
            if (BlockState.valueOf(bs[i]) == BlockState.HIT) {
                elm?.innerHTML = i.toString() + ") HIT"
                elm?.setAttribute("style", "background-color:#00d1b2;")
            } else if (BlockState.valueOf(bs[i]) == BlockState.MISS) {
                elm?.innerHTML = i.toString() + ") MISS"
                elm?.setAttribute("style", "background-color:#ff4e4e;")
            } else {
                elm?.innerHTML = i.toString() + ") EMPTY"
                elm?.setAttribute("style", "")
            }
        }
        updateCacheBlocks()
    }

    /**
     * Updates the PC to the given value. It also highlights the to-be-executed instruction.
     *
     * @param pc the new PC
     * @todo abstract away instruction length
     */
    fun updatePC(pc: Number) {
//        val idx = (pc.toInt() - MemorySegments.TEXT_BEGIN) / 4
//        val idx = sim.invInstOrderMapping[pc.toInt()]
        val idx = pc.toInt()
        activeInstruction?.classList?.remove("is-selected")
        val newActiveInstruction = document.getElementById("instruction-$idx") as HTMLElement?
        newActiveInstruction?.classList?.add("is-selected")

        // Only scroll if out of view
        if (newActiveInstruction != null) {
            val listingContainer = document.getElementById("program-listing-container-parent") as HTMLElement
            val containerHeight = listingContainer.offsetHeight
            if (newActiveInstruction.getBoundingClientRect().top -
                    listingContainer.getBoundingClientRect().top < 0) {
                newActiveInstruction.scrollIntoView(true)
            } else if (newActiveInstruction.getBoundingClientRect().bottom -
                    listingContainer.getBoundingClientRect().top > containerHeight) {
                newActiveInstruction.scrollIntoView(false)
            }
        }

        activeInstruction = newActiveInstruction
    }

    /**
     * Prints the given thing to the console as a string.
     *
     * @param thing the thing to print
     */
    internal fun printConsole(thing: Any) {
        try {
            val console = getElement("console-output") as HTMLTextAreaElement
            console.value += thing.toString()
        } catch (e: ClassCastException) {
            console.log("Failed to print to console: $e\nMessage: $thing")
        }
    }

    /**
     * Clears the console
     */
    fun clearConsole() {
        try {
            val console = getElement("console-output") as HTMLTextAreaElement
            console.value = ""
        } catch (e: ClassCastException) {
            console.log("Failed to clear console: $e")
        }
    }

    /**
     * Sets whether the run button is spinning.
     *
     * @param spinning whether the button should be spin
     */
    fun setRunButtonSpinning(spinning: Boolean) {
        val runButton = getElement("simulator-run")
        if (spinning) {
            runButton.classList.add("is-loading")
            disableControlButtons()
        } else {
            runButton.classList.remove("is-loading")
            updateControlButtons()
        }
    }

    /**
     * Sets whether the name button is spinning.
     *
     * @param spinning whether the button should be spin
     */
    fun setNameButtonSpinning(name: String, spinning: Boolean) {
        val runButton = getElement(name)
        if (spinning) {
            runButton.classList.add("is-loading")
            disableControlButtons()
        } else {
            runButton.classList.remove("is-loading")
            updateControlButtons()
        }
    }
    /**
     * Sets whether a button is disabled.
     *
     * @param id the id of the button to change
     * @param disabled whether or not to disable the button
     */
    private fun setButtonDisabled(id: String, disabled: Boolean) {
        val button = getElement(id) as HTMLButtonElement
        button.disabled = disabled
    }

    /**
     * Renders the control buttons to be enabled / disabled appropriately.
     */
    fun updateControlButtons() {
        setButtonDisabled("simulator-reset", sim.getCycles() == 0)
        setButtonDisabled("simulator-undo", !sim.canUndo())
        setButtonDisabled("simulator-step", sim.isDone())
        setButtonDisabled("simulator-run", sim.isDone())
        setButtonDisabled("simulator-trace", sim.instOrderMapping.isEmpty() or sim.isDone())
    }

    /**
     * Disables the step, undo and reset buttons.
     *
     * Used while running, see [Driver.runStart].
     */
    fun disableControlButtons() {
        setButtonDisabled("simulator-reset", true)
        setButtonDisabled("simulator-undo", true)
        setButtonDisabled("simulator-step", true)
    }

    /**
     * Renders a change in breakpoint status
     *
     * @param idx the index to render
     * @param state whether or not there is a breakpoint
     */
    fun renderBreakpointAt(idx: Int, state: Boolean) {
        val row = getElement("instruction-$idx")
        if (state) {
            row.classList.add("is-breakpoint")
            row.querySelector("[name=\"breakpoint_cell\"]")?.addClass("is-breakpoint")
        } else {
            row.classList.remove("is-breakpoint")
            row.querySelector("[name=\"breakpoint_cell\"]")?.removeClass("is-breakpoint")
        }
    }

    /**
     * Number of rows to show around the current address
     */
    const val MEMORY_CONTEXT = 6

    fun rendererAddPackage(pid: String, enabled: Boolean, removable: Boolean = true) {
        val rp = document.createElement("div")
        rp.addClass("panel-block")
        rp.id = "package-$pid"

        val name = document.createElement("div")
        name.innerHTML = pid
        rp.appendChild(name)

        val enabledButton = document.createElement("button")
        enabledButton.id = "penable-button-$pid"
        enabledButton.addClass("button")
        if (enabled) {
            enabledButton.addClass("is-primary")
        }
        enabledButton.setAttribute("onclick", "this.classList.add('is-loading');driver.togglePackage('$pid')")
        enabledButton.innerHTML = "Enabled"
        rp.appendChild(enabledButton)

        if (removable) {
            val deleteButton = document.createElement("button")
            deleteButton.id = "pdelete-button-$pid"
            deleteButton.addClass("button")
            deleteButton.setAttribute("onclick", "this.classList.add('is-loading');driver.removePackage('$pid')")
            deleteButton.setAttribute("style", "background-color: red;")
            deleteButton.innerHTML = "Delete"
            rp.appendChild(deleteButton)
        }

        document.getElementById("package-list")?.appendChild(rp)
    }

    fun rendererRemovePackage(pid: String) {
        document.getElementById("package-$pid")?.remove()
    }

    fun rendererUpdatePackage(pid: String, state: Boolean) {
        val p = document.getElementById("penable-button-$pid")
        if (p != null) {
            if (state) {
                p.addClass("is-primary")
            } else {
                p.removeClass("is-primary")
            }
            p.removeClass("is-loading")
        } else {
            console.log("Could not find package '$pid!'")
        }
    }

    var pkgmsgTimeout: Int? = null
    fun pkgMsg(m: String) {
        if (pkgmsgTimeout != null) {
            window.clearTimeout(pkgmsgTimeout ?: -1)
        }
        val d = document.getElementById("package-msgs")
        d?.innerHTML = m
        pkgmsgTimeout = window.setTimeout(Renderer::clearPkgMsg, 10000)
    }

    fun clearPkgMsg() {
        document.getElementById("package-msgs")?.innerHTML = ""
    }

    /**
     * Update the [MEMORY_CONTEXT] words above and below the given address.
     *
     * Does not shift the memory display if it can be avoided
     *
     * @param addr the address to update around
     */
    fun updateMemory(addr: Int) {
        val wordAddress = (addr shr 2) shl 2
        if (mustMoveMemoryDisplay(wordAddress)) {
            activeMemoryAddress = wordAddress
        }

        for (rowIdx in -MEMORY_CONTEXT..MEMORY_CONTEXT) {
//        val rowIdx = 6
            val row = getElement("mem-row-$rowIdx")
            val rowAddr = activeMemoryAddress + 4 * rowIdx
            renderMemoryRow(row, rowAddr)
        }
    }

    /**
     * Determines if we need to move the memory display to show the address
     *
     * @param wordAddress the address we want to show
     * @return true if we need to move the display
     */
    private fun mustMoveMemoryDisplay(wordAddress: Int) =
            (activeMemoryAddress - wordAddress) shr 2 !in -MEMORY_CONTEXT..MEMORY_CONTEXT

    /**
     * Renders a row of the memory.
     *
     * @param row the HTML element of the row to render
     * @param rowAddr the new address of that row
     */
    fun displayByteAsString(byte: Int): String {
        return when (displayType) {
            "Hex" -> byteToHex(byte)
            "Decimal" -> byteToDec(byte)
            "Unsigned" -> byteToUnsign(byte)
            "ASCII" -> toAscii(byte, 2)
            else -> byteToHex(byte)
        }
    }
    private fun renderMemoryRow(urow: HTMLElement, rowAddr: Int) {
        val row = cleanTableRow(urow)
        val tdAddress = row.childNodes[0] as HTMLTableCellElement
        if (rowAddr >= 0) {
            tdAddress.innerText = toHex(rowAddr)
            for (i in 1..4) {
                val tdByte = row.childNodes[i] as HTMLTableCellElement
                val input = tdByte.childNodes[0] as HTMLInputElement
                val byte = sim.loadByte(rowAddr + i - 1)
                input.value = displayByteAsString(byte)
                input.disabled = false
                js("input.onkeydown();")
            }
        } else {
            tdAddress.innerText = "----------"
            for (i in 1..4) {
                val tdByte = row.childNodes[i] as HTMLTableCellElement
                val input = tdByte.childNodes[0] as HTMLInputElement
                input.value = "--"
                input.disabled = true
                js("input.onkeydown();")
            }
        }
    }

    private fun cleanTableRow(row: HTMLElement): HTMLElement {
        for (n in row.childNodes.asList()) {
            if (n !is HTMLTableCellElement) {
                row.removeChild(n)
            }
        }
        return row
    }

    /** a map from integers to the corresponding hex digits */
    private val hexMap = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F')

    /**
     * Convert a certain byte to hex
     *
     * @param b the byte to convert
     * @return a hex string for the byte
     *
     * @throws IndexOutOfBoundsException if b is not in -127..255
     */
    private fun byteToHex(b: Int): String {
        val leftNibble = hexMap[b ushr 4]
        val rightNibble = hexMap[b and 15]
        return "$leftNibble$rightNibble"
    }

    private fun byteToDec(b: Int): String = b.toByte().toString()

    private fun byteToUnsign(b: Int): String = b.toString()

    /**
     * Converts a value to a two's complement hex number.
     *
     * By two's complement, I mean that -1 becomes 0xFFFFFFFF not -0x1.
     *
     * @param value the value to convert
     * @return the hexadecimal string corresponding to that value
     * @todo move this?
     */
    fun toHex(value: Int, num_nibbles: Int = 8, add_prefix: Boolean = true): String {
        var remainder = value.toLong()
        var suffix = ""

        repeat(num_nibbles) {
            val hexDigit = hexMap[(remainder and 15).toInt()]
            suffix = hexDigit + suffix
            remainder = remainder ushr 4
        }

        if (add_prefix) {
            suffix = "0x" + suffix
        }

        return suffix
    }

    fun toHex(value: Number): String {
        return toHex(value.toInt())
    }

    private fun toUnsigned(value: Int): String =
            if (value >= 0) value.toString() else (value + 0x1_0000_0000L).toString()

    private fun toAscii(value: Int, num_nibbles: Int = 8): String {
        var s = ""
//        for (i in 0..3) {
//            val v = (value shr i * 8) and 0xFF
            val v = value
            s += when (v) {
                !in 0..255 -> toHex(v, num_nibbles = num_nibbles)
//                !in 32..126 -> "\uFFFD"
                !in 32..126 -> toHex(v, num_nibbles = num_nibbles)
                else -> "${v.toChar()}"
            }
//        }
        return s
    }

    /**
     * Sets the display type for all of the registers and memory
     * Rerenders after
     */
    fun updateRegMemDisplay() {
        val displaySelect = getElement("display-settings") as HTMLSelectElement
        displayType = displaySelect.value
        updateAll()
    }

    fun moveMemoryJump() {
        val jumpSelect = getElement("address-jump") as HTMLSelectElement
        val where = jumpSelect.value
        activeMemoryAddress = when (where) {
            "Text" -> MemorySegments.TEXT_BEGIN
            "Data" -> MemorySegments.STATIC_BEGIN
            "Heap" -> MemorySegments.HEAP_BEGIN
            "Stack" -> MemorySegments.STACK_BEGIN
            else -> MemorySegments.TEXT_BEGIN
        }
        updateMemory(activeMemoryAddress)
        jumpSelect.selectedIndex = 0
    }

    private fun moveMemoryBy(rows: Int) {
        val bytes = 4 * rows
        if (activeMemoryAddress + bytes < 0) return
        activeMemoryAddress += bytes
        updateMemory(activeMemoryAddress)
    }

    fun moveMemoryUp() = moveMemoryBy(MEMORY_CONTEXT)
    fun moveMemoryDown() = moveMemoryBy(-MEMORY_CONTEXT)

    fun updateText() {
        var t = (document.getElementById("text-start") as HTMLInputElement)
        t.value = intToString(userStringToInt(t.value))
    }

    fun renderButton(e: HTMLButtonElement, b: Boolean) {
        if (b) {
            e.classList.add("is-primary")
        } else {
            e.classList.remove("is-primary")
        }
        e.value = b.toString()
    }

    fun addObjectToDisplay(obj: VFSObject, special: String = "") {
        val b = document.getElementById("files-listing-body")!!
        var elm = document.createElement("tr")
        if (special == "") {
            elm.setAttribute("id", obj.label)

            val objlabel = if (obj.type == VFSType.Drive) {
                var styleclass = "terminal-drive"
                if (obj.isMounted()) {
                    styleclass = if (obj.mountedHandler!!.validate_connection() == "") {
                        "terminal-drive-mounted-connected"
                    } else {
                        "terminal-drive-mounted-disconnected"
                    }
                }
                "<font class=$styleclass>${obj.label}</font>"
            } else {
                obj.label
            }

            elm.innerHTML = "<td>$objlabel</td>"
            elm.innerHTML += "<td>${obj.type.name}</td>"
            var options = "<td>"

            when (obj) {
                is VFSDrive -> {
                    options += "<button class=\"button is-primary\" onclick=\"driver.openVFObject('${obj.getPath()}')\">Open</button>\n"
                }
                is VFSFile -> {
                    options += "<button class=\"button is-primary\" onclick=\"driver.editVFObject('${obj.getPath()}')\">Edit</button>\n"
                    options += "<button class=\"button is-primary\" onclick=\"driver.saveVFObject('${obj.getPath()}')\">Save</button>\n"
                    options += "<button class=\"button is-primary\" onclick=\"driver.vdbVFObject('${obj.getPath()}')\">VDB</button>\n"
                }
                is VFSFolder -> {
                    options += "<button class=\"button is-primary\" onclick=\"driver.openVFObject('${obj.getPath()}')\">Open</button>\n"
                }
                is VFSLinkedProgram -> {
                    // TODO
                }
                is VFSProgram -> {
                    // TODO
                }
                else -> {
                    // TODO
                }
            }
            options += "<button class=\"button is-primary\" style=\"background-color:red;\" " +
                    "onclick=\"driver.deleteVFObject('${obj.getPath()}')\">Delete</button>"
            options += "</td>"

            elm.innerHTML += options
        } else {
            elm.setAttribute("id", special)
            elm.innerHTML = "<td>$special</td>"
            elm.innerHTML += "<td>${obj.type.name}</td>"
            var options = "<td>"
            options += "<button class=\"button is-primary\" onclick=\"driver.openVFObject('$special')\">Open</button>\n"
            options += "</td>"
            elm.innerHTML += options
        }
        b.appendChild(elm)
    }

    fun addFilePWD(obj: VFSObject) {
        var b = document.getElementById("files-listing-pwd")!!
        var pwd = ""
        var o = obj
        while (o != o.parent && o.parent.type != VFSType.Dummy) {
            val path = o.getPath()
            val extra = if (o.type == VFSType.Drive) {
                var styleclass = "terminal-drive"
                if (o.isMounted()) {
                    styleclass = if (o.mountedHandler!!.validate_connection() == "") {
                        "terminal-drive-mounted-connected"
                    } else {
                        "terminal-drive-mounted-disconnected"
                    }
                }
                "class=$styleclass"
            } else {
                ""
            }
            pwd = "<a $extra onclick=\"driver.openVFObject('$path')\">${o.label}</a>/" + pwd
            o = o.parent
        }
        val path = o.getPath()
        val extra = if (o.type == VFSType.Drive) {
            var styleclass = "terminal-drive"
            if (o.isMounted()) {
                styleclass = if (o.mountedHandler!!.validate_connection() == "") {
                    "terminal-drive-mounted-connected"
                } else {
                    "terminal-drive-mounted-disconnected"
                }
            }
            "class=$styleclass"
        } else {
            ""
        }
        pwd = "<a $extra onclick=\"driver.openVFObject('$path')\">${o.label}</a>/" + pwd
        b.innerHTML = pwd
    }

    fun clearObjectsFromDisplay() {
        var b = document.getElementById("files-listing-body")!!
        b.innerHTML = ""
        b = document.getElementById("files-listing-pwd")!!
        b.innerHTML = ""
    }
}
