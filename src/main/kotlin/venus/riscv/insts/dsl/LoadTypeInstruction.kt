package venus.riscv.insts.dsl

import venus.riscv.insts.dsl.disasms.base.LoadDisassembler
import venus.riscv.insts.dsl.formats.base.ITypeFormat
import venus.riscv.insts.dsl.impls.base.LoadImplementation32
import venus.riscv.insts.dsl.impls.NoImplementation
import venus.riscv.insts.dsl.parsers.base.LoadParser
import venus.simulator.Simulator

class LoadTypeInstruction(
    name: String,
    opcode: Int,
    funct3: Int,
    load16: (Simulator, Short) -> Short = { _, _ -> throw NotImplementedError("no rv16") },
    postLoad16: (Short) -> Short = { it },
    load32: (Simulator, Int) -> Int,
    postLoad32: (Int) -> Int = { it },
    load64: (Simulator, Long) -> Long = { _, _ -> throw NotImplementedError("no rv64") },
    postLoad64: (Long) -> Long = { it },
    load128: (Simulator, Long) -> Long = { _, _ -> throw NotImplementedError("no rv128") },
    postLoad128: (Long) -> Long = { it }
) : Instruction(
        name = name,
        format = ITypeFormat(opcode, funct3),
        parser = LoadParser,
        impl16 = NoImplementation,
        impl32 = LoadImplementation32(load32, postLoad32),
        impl64 = NoImplementation,
        impl128 = NoImplementation,
        disasm = LoadDisassembler
)
