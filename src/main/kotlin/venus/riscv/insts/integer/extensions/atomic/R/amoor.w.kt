package venus.riscv.insts.integer.extensions.atomic.r

import venus.riscv.insts.dsl.AMORTypeInstruction

val amoorw = AMORTypeInstruction(
        name = "amoor.w",
        opcode = 0b0101111,
        funct3 = 0b010,
        funct5 = 0b01000,
        rl = 0b0,
        aq = 0b0,
        // eval16 = { a, b -> (a + b).toShort() },
        eval32 = { data, vrs2 -> data or vrs2 }
        // eval64 = { a, b -> a + b },
        // eval128 = { a, b -> a + b }
)

val amoorwaq = AMORTypeInstruction(
        name = "amoor.w.aq",
        opcode = 0b0101111,
        funct3 = 0b010,
        funct5 = 0b01000,
        rl = 0b0,
        aq = 0b1,
        // eval16 = { a, b -> (a + b).toShort() },
        eval32 = { data, vrs2 -> data or vrs2 }
        // eval64 = { a, b -> a + b },
        // eval128 = { a, b -> a + b }
)

val amoorwrl = AMORTypeInstruction(
        name = "amoor.w.rl",
        opcode = 0b0101111,
        funct3 = 0b010,
        funct5 = 0b01000,
        rl = 0b1,
        aq = 0b0,
        // eval16 = { a, b -> (a + b).toShort() },
        eval32 = { data, vrs2 -> data or vrs2 }
        // eval64 = { a, b -> a + b },
        // eval128 = { a, b -> a + b }
)

val amoorwaqrl = AMORTypeInstruction(
        name = "amoor.w.aq.rl",
        opcode = 0b0101111,
        funct3 = 0b010,
        funct5 = 0b01000,
        rl = 0b1,
        aq = 0b1,
        // eval16 = { a, b -> (a + b).toShort() },
        eval32 = { data, vrs2 -> data or vrs2 }
        // eval64 = { a, b -> a + b },
        // eval128 = { a, b -> a + b }
)

val amoorwrlaq = AMORTypeInstruction(
        name = "amoor.w.rl.aq",
        opcode = 0b0101111,
        funct3 = 0b010,
        funct5 = 0b01000,
        rl = 0b1,
        aq = 0b1,
        // eval16 = { a, b -> (a + b).toShort() },
        eval32 = { data, vrs2 -> data or vrs2 }
        // eval64 = { a, b -> a + b },
        // eval128 = { a, b -> a + b }
)