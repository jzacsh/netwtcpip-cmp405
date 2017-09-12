/**
 * Inspects an IP ethernet frame, given as a string of hex values.
 *
 * Sample of valid input can be retrieved by:
 *   `git show 341a73f6d601:lecture03_20170911.adoc  | sed -n '93,100'p`
 */
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/*max-size of collected frags of an ip datagram*/
#define MAX_HEX_STREAM_LEN 65000

#define PRETTY_PRINT_HORIZ "------------------------------------------------------------"

struct frame {
  // Source hex values from which below fields are parsed.
  char srcHex[MAX_HEX_STREAM_LEN];
  int srcLen;

  int cursor;

  // Parsed ethernet frame header fields.
  char ethfrm_dstHwAddr[6*2];
  char ethfrm_srcHwAddr[6*2];
  char ethfrm_type[2*2];

  // Parsed ethernet frame payload below this line...
  // TODO(zacsh) complete
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

void printFrameHex(struct frame *frm) {
  for (int i = 0; i < frm->srcLen; ++i) {
    printf("%c", frm->srcHex[i]);
    if (i % 2) { printf(" "); }
  }
}

/** Returns error code if fails to parse frame data. */
int parseFrame(struct frame *frm) {
  memcpy(frm->ethfrm_dstHwAddr, frm->srcHex+frm->cursor, sizeof(frm->ethfrm_dstHwAddr));
  frm->cursor += sizeof(frm->ethfrm_dstHwAddr);

  memcpy(frm->ethfrm_srcHwAddr, frm->srcHex+frm->cursor, sizeof(frm->ethfrm_srcHwAddr));
  frm->cursor += sizeof(frm->ethfrm_srcHwAddr);

  memcpy(frm->ethfrm_type, frm->srcHex+frm->cursor, sizeof(frm->ethfrm_type));
  frm->cursor += sizeof(frm->ethfrm_type);

  return 0;
}

/* expects `dst` is size +1 large */
void formatHex(char *src, char *dst, int size) {
  memset(dst, '\0', size + 1);

  char buff[size*2/*overestimate*/];
  memset(buff, '\0', sizeof(buff));
  int srci, outi;
  for (srci = 0, outi = 0; srci < size; ++outi) {
    buff[outi] = src[srci];

    srci++;
    if (srci && !(srci % 2)) {
      outi++;
      buff[outi] = ' ';
    }
  }
  if (buff[outi-1] == ' ') {
    buff[outi-1] = '\0';
  }
  memcpy(dst, buff, size*2);
}

/** Pretty prints the contents of frm. */
void printFrame(struct frame *frm) {
  char fmtBuff[MAX_HEX_STREAM_LEN];
  memset(fmtBuff, '\0', MAX_HEX_STREAM_LEN);

  printf("\n");

  printf("Ethernet Frame Headers:\n%s\n", PRETTY_PRINT_HORIZ);

  formatHex(frm->ethfrm_dstHwAddr, fmtBuff, sizeof(frm->ethfrm_dstHwAddr));
  printf("destin hardware address: %s\n", fmtBuff);

  formatHex(frm->ethfrm_srcHwAddr, fmtBuff, sizeof(frm->ethfrm_srcHwAddr));
  printf("source hardware address: %s\n", fmtBuff);

  formatHex(frm->ethfrm_type, fmtBuff, sizeof(frm->ethfrm_type));
  printf("frame type: %s\n", fmtBuff);

  printf("\n");
  printf("Ethernet Frame Payload (IP Frame):\n%s\n", PRETTY_PRINT_HORIZ);

  printf("\n");
}

int main(int argc, char **argv) {
  int status = 0;
  struct frame *frm = NULL;
  if (!(frm = malloc(sizeof(struct frame)))) {
    perror("could not alloc space for an ethernet frame");
    status = 1;
    goto cleanup;
  }
  memset(frm, '\0', sizeof(struct frame));

  if (!(frm->srcLen = readHexFrom(frm->srcHex, STDIN_FILENO, MAX_HEX_STREAM_LEN))) {
    fprintf(stderr, "error: no frame data found on stdin\n");
    status = 1;
    goto cleanup;
  }

  printf("Got %d hex characters\n", frm->srcLen);
  if (parseFrame(frm) < 0) {
    fprintf(stderr, "error: parsing frame data\n");
    status = 1;
    goto cleanup;
  }

  printFrame(frm);

cleanup:
  if (frm != NULL) {
    free(frm);
  }
  return status;
}
