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

#define IS_DEBUG 0

/*max-size of collected frags of an ip datagram*/
#define MAX_HEX_STREAM_LEN 65000

#define PRETTY_PRINT_HORIZ "------------------------------------------------------------"

struct frame {
  unsigned char src[MAX_HEX_STREAM_LEN]; // not yet ready: use `srcHex` field

  // Source hex values from which below fields are parsed.
  char srcHex[MAX_HEX_STREAM_LEN]; // deprecated: use `src` field
  int srcLen;
  int cursor;  // internal state used by parser

  // Parsed ethernet frame header fields.
  unsigned char ethframe_dstHwAddr[6];
  unsigned char ethframe_srcHwAddr[6];
  unsigned char ethframe_type[2];

  ///////////////////////////////////////////////////
  // Parsed ethernet frame payload below this line...

  // Protocol version; typically `4` indicating IPv4
  unsigned char ipfrm_version; // 4 bits

  // Count of two-byte groups occuring in header before payload (typically `5`)
  //
  // Necessary in case "optional" header fields are utilized, allowing IP
  // payload to eventually be found.
  unsigned char ipfrm_headerlen; // 4 bits


  // Field "type of service"
  unsigned char ipfrm_serviceType; // 1 byte

  // TODO(zacsh) complete
};

int readHexFrom(unsigned char *output, int srcFile, int outLimit) {
  char hex; // [0,f]=[0,15] -- a raw input string
  unsigned char dec; // same, converted to decimal value

  int ini = 0;
  int outi = 0;
  while (read(srcFile, &hex, 1) > 0) {
    if (isspace(hex)) {
      continue;
    }

    dec = (unsigned char) strtol(&hex, NULL, 16);
    ini++;

    if (IS_DEBUG) fprintf(stderr, "ini(%d); outi(%d); %d, hex: %d or %x\n",
        ini, outi, (ini-1) % 2, dec, dec);

    if (!((ini-1) % 2)) {
      output[outi] = dec << 4;
      continue;
    }

    output[outi] |= dec;

    if (IS_DEBUG) {
      unsigned char le = (output[outi] & 0xf0) >> 4;
      unsigned char ri = output[outi] & 0x0f;
      fprintf(stderr, "\tdone packing: '%d', or %x %x\n", output[outi], le, ri);
    }

    outi++;
    if (outi >= outLimit) {
      break;
    }
  }
  if (IS_DEBUG) printf("\n");
  return outi;
}

void printFrameHex(struct frame *frm) {
  for (int i = 0; i < frm->srcLen; ++i) {
    printf("%c", frm->srcHex[i]);
    if (i % 2) { printf(" "); }
  }
}

/** Returns error code if fails to parse frame data. */
int parseFrame(struct frame *frm) {
  if (IS_DEBUG) fprintf(stderr, "starting parseFrame(...);\n");

  if (IS_DEBUG) fprintf(stderr, "sizeof dst hardware: %ld\n", sizeof(frm->ethframe_dstHwAddr));
  memcpy(frm->ethframe_dstHwAddr, frm->src+frm->cursor, sizeof(frm->ethframe_dstHwAddr));
  frm->cursor += sizeof(frm->ethframe_dstHwAddr);

  memcpy(frm->ethframe_srcHwAddr, frm->src+frm->cursor, sizeof(frm->ethframe_srcHwAddr));
  frm->cursor += sizeof(frm->ethframe_srcHwAddr);

  memcpy(frm->ethframe_type, frm->src+frm->cursor, sizeof(frm->ethframe_type));
  frm->cursor += sizeof(frm->ethframe_type);

  frm->ipfrm_version = (frm->src[frm->cursor] & 0xf0) >> 4;
  frm->ipfrm_headerlen = frm->src[frm->cursor] & 0x0f;
  frm->cursor++;

  frm->ipfrm_serviceType = frm->src[frm->cursor];
  frm->cursor += sizeof(frm->ipfrm_serviceType);

  return 0;
}

/* expects `dst` is size +1 large */
void formatHex(unsigned char *src, char *dst, int size) {
  memset(dst, '\0', size + 1);

  int srci, outi;
  for (srci = 0, outi = 0; srci < size; outi += 3) {
    snprintf(dst+outi, 4 /*2 hex chars + space + '\0' */, "%02X ", src[srci]);
    srci++;
  }

  if (dst[outi-1] == ' ') {
    dst[outi-1] = '\0';
  }
}

/** Pretty prints the contents of frm. */
void printFrame(struct frame *frm) {
  char fmtBuff[MAX_HEX_STREAM_LEN];
  memset(fmtBuff, '\0', MAX_HEX_STREAM_LEN);

  printf("\nEthernet Frame Headers:\n%s\n", PRETTY_PRINT_HORIZ);

  formatHex(frm->ethframe_dstHwAddr, fmtBuff, sizeof(frm->ethframe_dstHwAddr));
  printf("destin hardware address: %s\n", fmtBuff);

  formatHex(frm->ethframe_srcHwAddr, fmtBuff, sizeof(frm->ethframe_srcHwAddr));
  printf("source hardware address: %s\n", fmtBuff);

  formatHex(frm->ethframe_type, fmtBuff, sizeof(frm->ethframe_type));
  printf("frame type: %s\n", fmtBuff);

  printf("\nEthernet Frame Payload (IP Frame):\n%s\n", PRETTY_PRINT_HORIZ);

  printf("version: %d [hex: %02X]\n", frm->ipfrm_version, frm->ipfrm_version);

  printf("header len: %d [hex: %02X]\n", frm->ipfrm_headerlen, frm->ipfrm_headerlen);

  printf("service type: %02X\n", frm->ipfrm_serviceType);

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

  if (!(frm->srcLen = readHexFrom(frm->src, STDIN_FILENO, MAX_HEX_STREAM_LEN))) {
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
