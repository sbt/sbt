#include "lib.h"

#define __STR(x) #x
#define STR(x) __STR(x)

#define BODY(x, op) x op x
#define OP *
#define ARG x

const int func(const int x) {
    return BODY(ARG, OP);
}

const char* func_str() {
    return BODY(STR(ARG), " "STR(OP)" ");
}
