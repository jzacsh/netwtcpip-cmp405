/**
 * Inspects an IP ethernet frame, given as a string of hex values.
 *
 * Sample of valid input can be retrieved by:
 *   `git show 341a73f6d601:lecture03_20170911.adoc  | sed -n '93,100'p`
 */
#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define IS_DEBUG 0

/*max-size of collected frags of an ip datagram*/
#define MAX_HEX_STREAM_LEN 65000

#define PRETTY_PRINT_HORIZ "------------------------------------------------------------"

struct frame {
  // Source hex values from which below fields are parsed.
  unsigned char src[MAX_HEX_STREAM_LEN];
  int srcLen;
  int cursor;  // internal state used by parser

  // IP & Ethernet frame fields (prefixed as such) are below. Each indicates its
  // size in bytes which matches the protocol. Where protocol is not on
  // byte-boundaries, the expected bit or byte size is indicated beside the
  // field.

  // Parsed ethernet frame header fields.
  unsigned char ethfrm_dstHwAddr[6];
  unsigned char ethfrm_srcHwAddr[6];
  unsigned char ethfrm_type[2];

  ///////////////////////////////////////////////////
  // Parsed ethernet frame payload below this line...

  // Protocol version; typically `4` indicating IPv4
  unsigned char ipfrm_version; // 4 bits

  // Field "internet header length" is the count of 4-byte groups (32-bit words)
  // occuring in the current header before payload (typically `5`).
  //
  // Necessary in case "optional" header fields are utilized, allowing IP
  // payload to eventually be found.
  unsigned char ipfrm_headerLen; // 4 bits

  // Field "type of service"
  unsigned char ipfrm_serviceType; // 1 byte

  // Field "total length" contains a byte-count of the entire ip frame,
  // including both header & payload.
  unsigned char ipfrm_totalLen[2];

  // Field "identificationa" used to identify disparate groups of fragments
  // (despite their order of arrival).
  unsigned char ipfrm_fragIdent[2];

  // TODO(zacsh) above are implemented; need to complete remaining fields

  // Field "flags" for fragments can be all off, or a combo of:
  // - 010: "DF" Don't Fragment
  // - 001: "MF" More Fragments
  // First bit is reserved and must be zero.
  unsigned char ipfrm_fragFlag; // 3 bits

  // Field "offset" for fragments is a integer index in [0,2^13) which fragment
  // indicating the byte-offset this payload represents within the larger
  // fragment group.
  unsigned char ipfrm_fragOffset[2]; // bits: 13 = 16 - 3
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

    errno = 0;
    dec = (unsigned char) strtol(&hex, NULL, 16);
    if (dec < 0 || dec > 15 || errno == ERANGE || errno != 0) {
      return -1;
    }
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

/** Returns error code if fails to parse frame data. */
int parseFrame(struct frame *frm) {
  if (IS_DEBUG) fprintf(stderr, "starting parseFrame(...);\n");

  if (IS_DEBUG) fprintf(stderr, "sizeof dst hardware: %ld\n", sizeof(frm->ethfrm_dstHwAddr));
  memcpy(frm->ethfrm_dstHwAddr, frm->src+frm->cursor, sizeof(frm->ethfrm_dstHwAddr));
  frm->cursor += sizeof(frm->ethfrm_dstHwAddr);

  memcpy(frm->ethfrm_srcHwAddr, frm->src+frm->cursor, sizeof(frm->ethfrm_srcHwAddr));
  frm->cursor += sizeof(frm->ethfrm_srcHwAddr);

  memcpy(frm->ethfrm_type, frm->src+frm->cursor, sizeof(frm->ethfrm_type));
  frm->cursor += sizeof(frm->ethfrm_type);

  frm->ipfrm_version = (frm->src[frm->cursor] & 0xf0) >> 4;
  frm->ipfrm_headerLen = frm->src[frm->cursor] & 0x0f;
  frm->cursor++;

  frm->ipfrm_serviceType = frm->src[frm->cursor];
  frm->cursor += sizeof(frm->ipfrm_serviceType);

  memcpy(frm->ipfrm_totalLen, frm->src+frm->cursor, sizeof(frm->ipfrm_totalLen));
  frm->cursor += sizeof(frm->ipfrm_totalLen);

  memcpy(frm->ipfrm_fragIdent, frm->src+frm->cursor, sizeof(frm->ipfrm_fragIdent));
  frm->cursor += sizeof(frm->ipfrm_fragIdent);

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

// Returns 0 on success, less than 0 on failure.
int getNum(unsigned char *src, unsigned long int *dst, int bytes) {
  *dst &= 0x00000000;
  if (bytes > 4) {
    fprintf(stderr, "trying to build an integer value from more than 32 bits, unexpected inside eth frame\n");
    return -1;
  }

  int end = bytes - 1;
  for (int i = 0; i < bytes; ++i) {
    if (IS_DEBUG) fprintf(stderr,
        "\t[dbg] src[i=%d]='%d' << %d --> %d\n",
        i, src[i], (end - i)*4,
        (0xff & src[i]) << ((end - i)*8));
    *dst |= (0xff & src[i]) << ((end - i)*8);
  }
  return 0;
}

/** Pretty prints the contents of frm. Return less than 0 indicates failure. */
int printFrame(struct frame *frm) {
  unsigned long int numBuff;
  char fmtBuff[MAX_HEX_STREAM_LEN];
  memset(fmtBuff, '\0', MAX_HEX_STREAM_LEN);

  printf("\nEthernet Frame Headers:\n%s\n", PRETTY_PRINT_HORIZ);

  formatHex(frm->ethfrm_dstHwAddr, fmtBuff, sizeof(frm->ethfrm_dstHwAddr));
  printf("destin hardware address: %s\n", fmtBuff);

  formatHex(frm->ethfrm_srcHwAddr, fmtBuff, sizeof(frm->ethfrm_srcHwAddr));
  printf("source hardware address: %s\n", fmtBuff);

  formatHex(frm->ethfrm_type, fmtBuff, sizeof(frm->ethfrm_type));
  printf("frame type: %s\n", fmtBuff);

  printf("\nEthernet Frame Payload (IP Frame):\n%s\n", PRETTY_PRINT_HORIZ);

  printf("version: %d [hex: %02X]\n", frm->ipfrm_version, frm->ipfrm_version);

  printf("header len: %d [hex: %02X]\n", frm->ipfrm_headerLen, frm->ipfrm_headerLen);

  printf("service type: %02X\n", frm->ipfrm_serviceType);

  formatHex(frm->ipfrm_totalLen, fmtBuff, sizeof(frm->ipfrm_totalLen));
  if (getNum(frm->ipfrm_totalLen, &numBuff, sizeof(frm->ipfrm_totalLen)) < 0) {
    return -1;
  }
  printf("total length: %ld [hex: %s]\n", numBuff, fmtBuff);

  formatHex(frm->ipfrm_fragIdent, fmtBuff, sizeof(frm->ipfrm_fragIdent));
  printf("(fragment) identification: %s\n", fmtBuff);

  printf("\n");
  return 0;
}

// TODO(zacsh) edge-cases not considered, eg:
// - no sanitation is done on input stream
int main(int argc, char **argv) {
  int status = 0;
  struct frame *frm = NULL;
  if (!(frm = malloc(sizeof(struct frame)))) {
    perror("could not alloc space for an ethernet frame");
    status = 1;
    goto cleanup;
  }
  memset(frm, '\0', sizeof(struct frame));

  if ((frm->srcLen = readHexFrom(frm->src, STDIN_FILENO, MAX_HEX_STREAM_LEN)) < 0) {
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

  printFrame(frm) < 0 ? status = 1 : 0;

cleanup:
  if (frm != NULL) {
    free(frm);
  }
  return status;
}
