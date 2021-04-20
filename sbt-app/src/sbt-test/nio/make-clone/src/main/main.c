#include "lib.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char *argv[]) {
  if (argc == 1) printf("No arguments provided, evaluating f with default value: 1\n");
  printf("f := %s\n", func_str());
  if (argc == 1) {
    printf("f(1) = %d\n", func(1));
  } else {
    for (int i = 1; i < argc; ++i) {
      int arg = atoi(argv[i]);
      printf("f(%d) = %d\n", arg, func(arg));
    }
  }
  return 0;
}
