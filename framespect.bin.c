/**
 * Inspects an IP ethernet frame, given as a string of hex values.
 *
 * Sample of valid input can be retrieved by:
 *   `git show 341a73f6d601:lecture03_20170911.adoc  | sed -n '93,100'p`
 */
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

/*max-size of collected frags of an ip datagram*/
#define MAX_HEX_STREAM_LEN 65000

struct frame {
  char srcHex[MAX_HEX_STREAM_LEN];
  int srcLen;
};

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
  int status = 0;
  struct frame *frm = NULL;
  if (!(frm = malloc(sizeof(struct frame)))) {
    perror("could not alloc space for an ethernet frame");
    status = 1;
    goto cleanup;
  }

  if (!(frm->srcLen = readHexFrom(frm->srcHex, STDIN_FILENO, MAX_HEX_STREAM_LEN))) {
    fprintf(stderr, "error: no frame data found on stdin\n");
    status = 1;
    goto cleanup;
  }

  printf("Got %d hex characters\n", frm->srcLen);
  for (int i = 0; i < frm->srcLen; i++) {
    printf("%c", frm->srcHex[i]);
    if (i % 2) { printf(" "); }
  }

cleanup:
  if (frm != NULL) {
    free(frm);
  }
  return status;
}
