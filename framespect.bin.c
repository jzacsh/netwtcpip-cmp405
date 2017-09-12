/**
 * Inspects an IP ethernet frame, given as a string of hex values.
 *
 * Sample of valid input can be retrieved by:
 *   `git show 341a73f6d601:lecture03_20170911.adoc  | sed -n '93,100'p`
 */
#include <stdio.h>
#include <unistd.h>
#include <ctype.h>

int readHexFrom(char *output, int srcFile, int outLimit) {
  char buff;
  int i = 0;
  while (read(srcFile, &buff, 1) > 0) {
    if (isspace(buff)) {
//    printf(" ");
      continue;
    }

    output[i] = buff;
//  printf("%c", output[i]);
    i++;

    if (i >= outLimit) {
      break;
    }
  }
//    printf("\n");
  return i;
}

int main(int argc, char **argv) {
  char hex[65000 /*max-size of collected frags of an ip datagram*/];

  int numHexChars;
  if (!(numHexChars = readHexFrom(hex, STDIN_FILENO, 65000 /*outLimit*/))) {
    fprintf(stderr, "error: no frame data found on stdin\n");
    return 1;
  }
  printf("Got %d hex characters\n", numHexChars);
}
