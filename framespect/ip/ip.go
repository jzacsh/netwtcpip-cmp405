// Package ip provides that highlevel behavior described in RFC 791 which is
// referred to as "ip module" responsibility, and which has been in the
// scope of my undergrad networking course (and my own research).
package ip

import (
	"fmt"

	"../blob"
)

// IPPayload is a FrameBody
type IPPayload struct {
	Blob blob.ByteBlob

	// Field "protocol version": typically `4` indicating IPv4
	ipVersion byte // warning: 4 bits

	// Field "internet header length" is the count of 4-byte groups (32-bit words)
	// occuring in the current header before payload (typically `5`).
	//
	// Necessary in case "optional" header fields are utilized, allowing IP
	// payload to eventually be found.
	ipHeaderLen byte // warning: 4 bits

	// Field "type of service"
	ipServiceType byte

	// Field "total length" contains a byte-count of the entire ip frame,
	// including both header & payload.
	ipTotalLen [2]byte

	// Field "identificationa" used to identify disparate groups of fragments
	// (despite their order of arrival).
	ipFragIdent [2]byte

	/* TODO(zacsh) remove the below block
	/////////////////////////////////////

	// Internal: contents of the last-half of the 32-bit "fragmentation" word of
	// the header. That is: the raw contents of flags + offset.
	octet_t _ipfrm_fragEndOfWord[2];

	// Field "flags" for fragments can be all off, or a combo of:
	// - 010: "DF" Don't Fragment
	// - 001: "MF" More Fragments
	// First bit is reserved and must be zero.
	octet_t ipfrm_fragFlag; // warning: 3 bits

	// Field "offset" for fragments is a integer index in [0,2^13) which fragment
	// indicating the byte-offset this payload represents within the larger
	// fragment group.
	octet_t ipfrm_fragOffset[2]; // warning: bits = 13 = 16 - 3

	// Field "TTL" is a decrementing-counter of hops allowed for a packet before
	// it should be dropped.
	octet_t ipfrm_timeToLive;

	// Field "Protocol" defines the protocol used in this IP frame's payload.
	// Values' semantics can be found here:
	// https://en.wikipedia.org/wiki/List_of_IP_protocol_numbers
	octet_t ipfrm_payloadProtocol;

	// Field "Header Checksum" is a checksum of the *header* fields (with the
	// checksum field itself set to zero) of the current frame.
	octet_t ipfrm_headerChecksum[2];

	// Field "Source IP Address"
	octet_t ipfrm_srcIPAddr[4];

	// Field "Destination IP Address"
	octet_t ipfrm_dstIPAddr[4];
	*/ // TODO(zacsh) implement in go, and remove
}

func (ipp *IPPayload) RawHeader() []byte { return ipp.Blob.Data }

func (ipp *IPPayload) HasHeader() bool { return len(ipp.Blob.Data) > 0 }

func (ipp *IPPayload) String() string {
	return fmt.Sprintf(
		`  version: %2d
  header len: %2d (# of 4-octets in header)
  service type: 0x% X
`, ipp.ipVersion, ipp.ipHeaderLen, ipp.ipServiceType)
}

// ParseHead takes a frame blob of bytes and returns two subsets or two nils and
// a parsing error. The two subsets of `blob` which a module should return are:
// - that beginning subset which the module identified as its own header
// - the remainder subset which the module identified as its own header
func (ipp *IPPayload) ParseHead() (IPPayload, PseudoAppModule, error) {
	versionAndHeader := ipp.Blob.Next(1)[0]
	ipp.ipVersion = (0xf0 & versionAndHeader) >> 4
	ipp.ipHeaderLen = 0x0f & versionAndHeader
	ipp.ipServiceType = ipp.Blob.Next(1)[0]
	// TODO(zacsh) complete this parsing
	return *ipp, PseudoAppModule{Unclaimed: ipp.Blob.Remainder()}, nil
}
